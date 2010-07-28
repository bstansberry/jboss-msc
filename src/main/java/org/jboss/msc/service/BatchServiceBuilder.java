/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.msc.service;

import java.util.Collection;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.value.Value;

/**
 * A builder for an individual service in a batch.  Create an instance via the
 * {@link BatchBuilder#addService(ServiceName, Service)}
 * or
 * {@link BatchBuilder#addServiceValue(ServiceName, Value)}
 * methods.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface BatchServiceBuilder<T> {

    /**
     * Add aliases for this service.
     *
     * @param aliases the service names to use as aliases
     * @return the builder
     */
    BatchServiceBuilder<T> addAliases(ServiceName... aliases);

    /**
     * Set the service definition location to be the caller's location.
     *
     * @return this builder
     */
    BatchServiceBuilder<T> setLocation();

    /**
     * Set the service definition location, if any.
     *
     * @param location the location
     * @return this builder
     */
    BatchServiceBuilder<T> setLocation(Location location);

    /**
     * Set the initial mode.
     *
     * @param mode the initial mode
     * @return this builder
     */
    BatchServiceBuilder<T> setInitialMode(ServiceController.Mode mode);

    /**
     * Add multiple, non-injected dependencies.
     *
     * @param dependencies the service names to depend on
     * @return this builder
     */
    BatchServiceBuilder<T> addDependencies(ServiceName... dependencies);

    /**
     * Add multiple, non-injected dependencies.
     *
     * @param dependencies the service names to depend on
     * @return this builder
     */
    BatchServiceBuilder<T> addDependencies(Iterable<ServiceName> dependencies);

    /**
     * Add a dependency.  Calling this method multiple times for the same service name will only add it as a
     * dependency one time; however this may be useful to specify multiple injections for one dependency.
     *
     * @param dependency the name of the dependency
     * @return an injection builder for optionally injecting the dependency
     * @deprecated Use {@link #addDependency(ServiceName, org.jboss.msc.inject.Injector)} instead.
     */
    BatchInjectionBuilder addDependency(ServiceName dependency);

    /**
     * Add a service dependency.  Calling this method multiple times for the same service name will only add it as a
     * dependency one time; however this may be useful to specify multiple injections for one dependency.
     *
     * @param dependency the name of the dependency
     * @param target the injector into which the dependency should be stored
     * @return this builder
     */
    BatchServiceBuilder<T> addDependency(ServiceName dependency, Injector<Object> target);

    /**
     * Add a service dependency.  The type of the dependency is checked before it is passed into the (type-safe) injector
     * instance.  Calling this method multiple times for the same service name will only add it as a
     * dependency one time; however this may be useful to specify multiple injections for one dependency.
     *
     * @param dependency the name of the dependency
     * @param type the class of the value of the dependency
     * @param target the injector into which the dependency should be stored
     * @param <I> the type of the value of the dependency
     * @return this builder
     */
    <I> BatchServiceBuilder<T> addDependency(ServiceName dependency, Class<I> type, Injector<I> target);

    /**
     * Add an injection value.
     *
     * @param value the value to inject
     * @return an injection builder for specifying the injection target
     * @deprecated Use {@link #addInjectionValue(org.jboss.msc.inject.Injector, Value)} instead.
     */
    BatchInjectionBuilder addInjectionValue(Value<?> value);

    /**
     * Add an injection.
     *
     * @param value the value to inject
     * @return an injection builder for specifying the injection target
     * @deprecated Use {@link #addInjection(org.jboss.msc.inject.Injector, Object)} instead.
     */
    BatchInjectionBuilder addInjection(Object value);

    /**
     * Add an injection.  The given value will be injected into the given injector before service start, and uninjected
     * after service stop.
     *
     * @param target the injection target
     * @param value the injection value
     * @param <I> the injection type
     * @return this builder
     */
    <I> BatchServiceBuilder<T> addInjection(Injector<? super I> target, I value);

    /**
     * Add an injection value.  The given value will be injected into the given injector before service start, and uninjected
     * after service stop.
     *
     * @param target the injection target
     * @param value the injection value
     * @param <I> the injection type
     * @return this builder
     */
    <I> BatchServiceBuilder<T> addInjectionValue(Injector<? super I> target, Value<I> value);

    /**
     * Add a service listener that will be added to this service.
     *
     * @param listener the listener to add to the service
     * @return this builder
     */
    BatchServiceBuilder<T> addListener(ServiceListener<? super T> listener);

    /**
     * Add service listeners that will be added to this service.
     *
     * @param listeners a list of listeners to add to the service
     * @return this builder
     */
    BatchServiceBuilder<T> addListener(ServiceListener<? super T>... listeners);

    /**
     * Add service listeners that will be added to this service.
     *
     * @param listeners a collection of listeners to add to the service
     * @return this builder
     */
    BatchServiceBuilder<T> addListener(Collection<? extends ServiceListener<? super T>> listeners);
}
