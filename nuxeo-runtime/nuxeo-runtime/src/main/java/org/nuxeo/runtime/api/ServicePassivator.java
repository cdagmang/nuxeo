/*******************************************************************************
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *******************************************************************************/
package org.nuxeo.runtime.api;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.nuxeo.runtime.api.ServicePassivator.Passivator.Accounting;
import org.nuxeo.runtime.api.ServicePassivator.Passivator.Accounting.InScopeOfContext;
import org.nuxeo.runtime.api.ServicePassivator.Termination.Failure;
import org.nuxeo.runtime.api.ServicePassivator.Termination.Success;
import org.nuxeo.runtime.model.ComponentManager;

/**
 * Blocks service accesses in order to run an operation which alter the runtime. That gives a way to prevent service
 * consumers to enter during the shutdown or the reload operation.
 * <p>
 * The invoke chain is split in the following steps
 * <dl>
 * <dt>passivate</dt>
 * <dd>intercept service lookup</dd>
 * <dt>monitor</dt>
 * <dd>monitor service pass-through accesses</dd>
 * <dt>await
 * <dt>
 * <dd>wait for the runtime being quiet before proceeding</dd>
 * <dt>proceed</dt>
 * <dd>proceed with the operation and handle termination hook</dd>
 * </dl>
 *
 * <pre>
 * ServicePassivator
 *         .passivate()
 *         .withQuietDelay(ChronoUnit.SECONDS.getDuration().multipliedBy(20))
 *         .monitor()
 *         .withTimeout(ChronoUnit.MINUTES.getDuration().multipliedBy(2))
 *         .await()
 *         .proceed(() -> System.out.println("do something"))
 *         .onFailure(failure -> System.out.println("failed " + failure))
 *         .onSuccess(() -> System.out.println("succeed"));*
 * </pre>
 * </p>
 *
 * @since 8.1
 */
public class ServicePassivator {

    public static Passivator passivate() {
        return new Passivator();
    }

    public static Termination proceed(Runnable runnable) {
        return passivate()
                        .monitor()
                        .await()
                        .proceed(runnable);
    }

    /**
     * Intercepts service lookups for implementing the quiet logic.
     */
    public static class Passivator {

        Passivator() {
            run();
        }

        final CountDownLatch achieved = new CountDownLatch(1);

        final Accounting accounting = new Accounting();

        Optional<ServiceProvider> installed = Optional.empty();

        void run() {
            installed = Optional.ofNullable(DefaultServiceProvider.getProvider());
            ServiceProvider passthrough = installed.map(
                            (Function<ServiceProvider, ServiceProvider>) DelegateProvider::new).orElseGet(
                                            (Supplier<ServiceProvider>) RuntimeProvider::new);
            ServiceProvider waitfor = new WaitForProvider(achieved, passthrough);
            PassivateProvider passivator = new PassivateProvider(Thread.currentThread(), accounting, waitfor,
                            passthrough);
            DefaultServiceProvider.setProvider(passivator);
        }

        void resetProvider() {
            PassivateProvider passivator = (PassivateProvider) DefaultServiceProvider.getProvider();
            ServiceProvider previous = passivator.passthrough instanceof DelegateProvider
                            ? ((DelegateProvider) passivator.passthrough).next
                            : null;
            DefaultServiceProvider.setProvider(previous);

        }

        void commit() {
            try {
                DefaultServiceProvider.setProvider(installed.orElse(null));
            } finally {
                achieved.countDown();
            }
        }

        TemporalAmount quietDelay = Duration.ofSeconds(2);

        public Passivator withQuietDelay(TemporalAmount delay) {
            quietDelay = delay;
            return this;
        }

        public Passivator peek(Consumer<Passivator> consumer) {
            consumer.accept(this);
            return this;
        }

        public Monitor monitor() {
            return new Monitor(this, quietDelay);
        }

        /**
         * Snapshots service lookups and states about service scoping. *
         */
        public class Accounting {

            /**
             * Takes a snapshot of the lookup.
             *
             * @param typeof
             * @return
             */
            Optional<InScopeOfContext> take(Class<?> serviceof) {
                Class<?>[] callstack = dumper.dump();
                Optional<InScopeOfContext> snapshot = inscopeof(callstack)
                                .map(inscopeof -> new InScopeOfContext(inscopeof, Thread.currentThread(), callstack));
                snapshot.ifPresent(this::register);
                return snapshot;
            }

            void register(InScopeOfContext context) {
                last = Optional.of(context);
            }

            volatile Optional<InScopeOfContext> last = Optional.empty();

            public Optional<InScopeOfContext> get() {
                return last;
            }

            Optional<InScopeOfContext> reset() {
                try {
                    return last;
                } finally {
                    last = Optional.empty();
                }
            }

            Optional<Class<?>> inscopeof(Class<?>[] callstack) {
                for (Class<?> typeof : callstack) {
                    if (manager.getComponentProvidingService(typeof) != null) {
                        return Optional.of(typeof);
                    }
                }
                return Optional.empty();
            }

            final ComponentManager manager = Framework.getRuntime().getComponentManager();

            final CallstackDumper dumper = new CallstackDumper();

            /**
             * Scoped service call context.
             */
            public class InScopeOfContext {

                InScopeOfContext(Class<?> serviceof, Thread thread, Class<?>[] callstack) {
                    this.serviceof = serviceof;
                    this.thread = thread;
                    this.callstack = callstack;
                }

                final Class<?> serviceof;

                final Thread thread;

                final Class<?>[] callstack;

                @Override
                public String toString() {
                    StringBuilder builder = new StringBuilder().append("on ")
                            .append(thread)
                            .append(" in scope of ")
                            .append(serviceof)
                            .append(System.lineSeparator());
                    for (Class<?> typeof : callstack) {
                        builder = builder.append("  ").append(typeof).append(System.lineSeparator());
                    }
                    return builder.toString();
                }
            }

            /**
             * Dumps caller stack and states for a service scope
             */
            class CallstackDumper extends SecurityManager {

                Class<?>[] dump() {
                    return super.getClassContext();
                }

            }

        }

    }

    /**
     * Monitors service lookups for stating about quiet status.
     */
    public static class Monitor {

        Monitor(Passivator passivator, TemporalAmount quietDelay) {
            this.passivator = passivator;
            this.quietDelay = quietDelay;
            run();
        }

        final Passivator passivator;

        final CountDownLatch passivated = new CountDownLatch(1);

        final TemporalAmount quietDelay;

        final Timer timer = new Timer(ServicePassivator.class.getSimpleName().toLowerCase());

        final TimerTask scheduledTask = new TimerTask() {

            @Override
            public void run() {
                Optional<InScopeOfContext> observed = passivator.accounting.reset();
                if (observed.isPresent()) {
                    return;
                }
                cancel();
                passivated.countDown();
            }
        };

        void run() {
            timer.scheduleAtFixedRate(
                            scheduledTask,
                            0,
                            TimeUnit.MILLISECONDS.convert(quietDelay.get(ChronoUnit.SECONDS), TimeUnit.SECONDS));
        }

        /**
         * Cancels service lookups monitoring.
         */
        void cancel() {
            try {
                timer.cancel();
            } finally {
                passivator.commit();
            }
        }

        TemporalAmount timeout = Duration.ofSeconds(10);

        public Monitor withTimeout(TemporalAmount timeout) {
            this.timeout = timeout;
            return this;
        }

        public Monitor peek(Consumer<Monitor> consumer) {
            consumer.accept(this);
            return this;
        }

        /**
         * Installs the timer task which monitor the service lookups. Once there will be no more lookups in the
         * scheduled
         * period, notifies the runner for proceeding.
         *
         * @param next
         * @return
         */
        public Waiter await() {
            return new Waiter(this, timeout);
        }

    }

    /**
     * Terminates the chain by running the operation in a passivated context.
     */
    public static class Waiter {

        Waiter(Monitor monitor, TemporalAmount timeout) {
            this.monitor = monitor;
            this.timeout = timeout;
        }

        final Monitor monitor;

        final TemporalAmount timeout;

        public Waiter peek(Consumer<Waiter> consumer) {
            consumer.accept(this);
            return this;
        }

        /**
         * Terminates the chain by invoking the operation
         * <ul>
         * <li>waits for the runtime being passivated,</li>
         * <li>and then runs the operation,</li>
         * <li>and then notifies the blocked lookup to proceed.</li>
         *
         * @param runnable
         *        the runnable to execute
         * @return the termination interface
         */
        public Termination proceed(Runnable runnable) {
            try {
                if (monitor.passivated.await(timeout.get(ChronoUnit.SECONDS), TimeUnit.SECONDS) == false) {
                } else {
                    runnable.run();
                }
                return monitor.passivator.accounting.last
                                .<Termination>map(Failure::new)
                                .orElseGet(Success::new);
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for passivation", cause);
            } finally {
                monitor.cancel();
            }
        }

    }

    /**
     * Terminates the pacification by a success or a failure action and release the lock.
     */
    public interface Termination {

        /**
         * Executes the runnable if the passivation was as success
         *
         * @param runnable
         */
        default Termination onSuccess(Runnable finisher) {
            return this;
        }

        /**
         * Recover the failure if the passivation was a failure, ie: some activity has been detected during the
         * protected
         * operation.
         *
         * @param runnable
         *        the failure action
         */
        default Termination onFailure(Consumer<InScopeOfContext> recoverer) {
            return this;
        }

        default Termination peek(Consumer<Termination> consumer) {
            consumer.accept(this);
            return this;
        }

        class Success implements Termination {

            @Override
            public Termination onSuccess(Runnable finisher) {
                finisher.run();
                return this;
            }

        }

        class Failure implements Termination {

            Failure(InScopeOfContext snapshot) {
                this.snapshot = snapshot;
            }

            final InScopeOfContext snapshot;

            @Override
            public Termination onFailure(Consumer<InScopeOfContext> recoverer) {
                recoverer.accept(snapshot);
                return this;
            }
        }
    }

    /**
     * Intercepts service lookups for blocking other threads.
     */
    static class PassivateProvider implements ServiceProvider {

        PassivateProvider(Thread ownerThread, Accounting accounting, ServiceProvider waitfor,
                        ServiceProvider passthrough) {
            this.ownerThread = ownerThread;
            this.accounting = accounting;
            this.waitfor = waitfor;
            this.passthrough = passthrough;
        }

        final Thread ownerThread;

        final Accounting accounting;

        final ServiceProvider passthrough;

        final ServiceProvider waitfor;

        @Override
        public <T> T getService(Class<T> typeof) {
            if (Thread.currentThread() == ownerThread) {
                return passthrough.getService(typeof);
            }
            return accounting
                            .take(typeof)
                            .map(snapshot -> passthrough)
                            .orElse(waitfor)
                            .getService(typeof);
        }
    }

    /**
     * Delegates the lookup to the previously installed service provider.
     */
    static class DelegateProvider implements ServiceProvider {

        DelegateProvider(ServiceProvider provider) {
            next = provider;
        }

        final ServiceProvider next;

        @Override
        public <T> T getService(Class<T> serviceClass) {
            return next.getService(serviceClass);
        }

    }

    /**
     * Let runtime resolve the service.
     */
    static class RuntimeProvider implements ServiceProvider {

        @Override
        public <T> T getService(Class<T> serviceClass) {
            return Framework.getRuntime().getService(serviceClass);
        }

    }

    /**
     * Waits for the condition before invoking the effective lookup.
     */
    static class WaitForProvider implements ServiceProvider {

        WaitForProvider(CountDownLatch condition, ServiceProvider passthrough) {
            this.condition = condition;
            this.passthrough = passthrough;
        }

        final CountDownLatch condition;

        final ServiceProvider passthrough;

        @Override
        public <T> T getService(Class<T> serviceClass) {
            try {
                condition.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + serviceClass);
            }
            return passthrough.getService(serviceClass);
        }

    }

}