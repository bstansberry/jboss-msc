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

import static java.lang.Thread.holdsLock;

/**
 * An OptionalDependency.<br>This class establishes a transitive dependency relationship between the
 * dependent and the real dependency. The intermediation performed by this class adds the required optional
 * behavior to the dependency relation, by:
 * <ul>
 * <li> notifies the dependent that it is in the UP state when the real dependency is unresolved or uninstalled</li>
 * <li> once the real dependency is installed, if there is a demand previously added by the dependent, this dependency
 *      does not start forwarding the notifications to the dependent, meaning that the dependent won't even be aware
 *      that the dependency is down</li>
 * <li> waits for the dependency to be installed and the dependent to be inactive, so it can finally start forwarding
 *      notifications in both directions (from dependency to dependent and vice-versa)</li>
 * </ul>
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
class OptionalDependency implements Dependency, Dependent {
    
    /**
     * One of the states of a dependency from the dependent point of view (i.e., based on notifications made by the
     * dependency).
     *
     */
    private static enum DependencyState {
        /**
         * The dependency is missing; means the last notification received by this {@code OptionalDependency} (as a
         * dependent of the real dependency) is {@link #dependencyUninstalled}.
         */
        MISSING,
        /**
         * The dependency is installed, but is not up. This is the initial state of the dependency. Also, if any
         * notification has been made by the dependency, this will be the dependency state if the last notification
         * received is {@link #dependencyInstalled}, {@link #dependencyDown}, or {@link #dependencyRetrying}.
         */
        INSTALLED,
        /**
         * The dependency failed; means the last notification received by this {@code OptionalDependency} (as a
         * dependent of the real dependency) is {@link #dependencyFailed}.
         */
        FAILED,
        /**
         * The dependency is up; means the last notification received by this {@code OptionalDependency} (as a
         * dependent of the real dependency) is {@link #dependencyUp}.
         */
        UP}

    /**
     * Indicates if optional dependency has a transitive dependency missing.
     */
    private boolean dependencyMissing = false;

    /**
     * The real dependency.
     */
    private final Dependency optionalDependency;

    /**
     * The {@link #optionalDependency} state, based on notifications that {@code optionalDependency} made to this 
     * dependent.
     */
    private DependencyState dependencyState;

    /**
     * Indicates whether a transitive dependency is missing.
     */
    private boolean notifyTransitiveDependencyMissing;

    /**
     * The dependent on this optional dependency
     */
    private Dependent dependent;

    /**
     * Indicates if this dependency has been demanded by the dependent 
     */
    private boolean demandedByDependent;

    /**
     * Indicates if notification should take place
     */
    boolean forwardNotifications;

    /**
     * Keeps track of whether optionalDependency has been notified of a dependent started.
     * This field is useful for avoiding dependentStopped notifications that don't have a
     * corresponding previous dependentStarted notification.
     */
    private boolean dependentStartedNotified = false;

    OptionalDependency(Dependency optionalDependency) {
        this.optionalDependency = optionalDependency;
        dependencyState = DependencyState.INSTALLED;
    }

    @Override
    public void addDependent(Dependent dependent) {
        assert !holdsLock(this);
        assert !holdsLock(dependent);
        final boolean notifyDependent;
        final DependencyState currentDependencyState;
        optionalDependency.addDependent(this);
        synchronized (this) {
            if (this.dependent != null) {
                throw new IllegalStateException("Optional dependent is already set");
            }
            this.dependent = dependent;
            notifyDependent = forwardNotifications = dependencyState.compareTo(DependencyState.INSTALLED) >= 0;
            currentDependencyState = dependencyState;
        }
        if (notifyDependent) {
            switch (currentDependencyState) {
                case FAILED:
                    dependent.dependencyFailed();
                    break;
                case UP:
                    dependent.immediateDependencyUp();
            }
            if (notifyTransitiveDependencyMissing) {
                dependent.transitiveDependencyUninstalled();
            }
        }
        else {
            dependent.immediateDependencyUp();
        }
    }

    @Override
    public void removeDependent(Dependent dependent) {
        assert !holdsLock(this);
        assert !holdsLock(dependent);
        synchronized (this) {
            dependent = null;
            forwardNotifications = false;
        }
        optionalDependency.removeDependent(this);
    }

    @Override
    public void addDemand() {
        assert !holdsLock(this);
        final boolean notifyOptionalDependency;
        synchronized (this) {
            demandedByDependent = true;
            notifyOptionalDependency = forwardNotifications;
        }
        if (notifyOptionalDependency) {
            optionalDependency.addDemand();
        }
    }

    @Override
    public void removeDemand() {
        assert !holdsLock(this);
        final boolean startNotifying;
        final boolean notifyOptionalDependency;
        final DependencyState currentDependencyState;
        final boolean transitiveDependencyMissing;
        synchronized (this) {
            demandedByDependent = false;
            currentDependencyState = dependencyState;
            transitiveDependencyMissing = this.notifyTransitiveDependencyMissing;
            if (forwardNotifications) {
                notifyOptionalDependency = true;
                startNotifying = false;
            } else {
                notifyOptionalDependency = false;
                startNotifying = forwardNotifications = (dependencyState.compareTo(DependencyState.INSTALLED) >= 0);
            }
        }
        if (startNotifying) {
            switch (currentDependencyState) {
                case INSTALLED:
                    dependent.immediateDependencyDown();
                    break;
                case FAILED:
                    dependent.dependencyFailed();
                    break;
            }
            // the status of missing and failed dependencies is changed now
            // that this optional dep is connected with the dependent
            if (transitiveDependencyMissing) {
                dependent.transitiveDependencyUninstalled();
            }
        } else if (notifyOptionalDependency) {
            optionalDependency.removeDemand();
        }
    }

    @Override
    public void dependentStarted() {
        assert !holdsLock(this);
        final boolean notifyOptionalDependency;
        synchronized (this) {
            dependentStartedNotified = notifyOptionalDependency = forwardNotifications;
        }
        if (notifyOptionalDependency) {
            optionalDependency.dependentStarted();
        }
    }

    @Override
    public void dependentStopped() {
        assert !holdsLock(this);
        final boolean notifyOptionalDependency;
        synchronized (this) {
            // on some multi-thread scenarios, it can happen that forwardNotification become true as the result of a
            // removeDemand call that is performed before dependentStopped. In this case, dependentStartedNotified
            // will prevent us from notify the dependency of a dependentStopped without a corresponding
            // previous dependentStarted notification
            notifyOptionalDependency = forwardNotifications && dependentStartedNotified;
            dependentStartedNotified = false;
        }
        if (notifyOptionalDependency) {
            optionalDependency.dependentStopped();
        }
    }

    @Override
    public Object getValue() throws IllegalStateException {
        assert !holdsLock(this);
        final boolean retrieveValue;
        synchronized (this) {
            retrieveValue = forwardNotifications;
        }
        return retrieveValue? optionalDependency.getValue(): null;
    }

    @Override
    public ServiceName getName() {
        return optionalDependency.getName();
    }

    @Override
    public void immediateDependencyInstalled(ServiceName dependencyName) {
        assert !holdsLock(this);
        final boolean notifyOptionalDependent;
        synchronized (this) {
            dependencyState = DependencyState.INSTALLED;
            forwardNotifications = notifyOptionalDependent = !demandedByDependent && dependent != null;
        }
        if (notifyOptionalDependent) {
            // need to update the dependent by telling it that the dependency is down
            dependent.immediateDependencyDown();
        }
    }

    @Override
    public void immediateDependencyUninstalled(ServiceName dependencyName) {
        assert !holdsLock(this);
        final boolean notificationsForwarded;
        final boolean demandNotified;
        final DependencyState currentDependencyState;
        final boolean currentDependencyMissing;
        synchronized (this) {
            currentDependencyState = this.dependencyState;
            notificationsForwarded = forwardNotifications;
            currentDependencyMissing = dependencyMissing;
            forwardNotifications = false;
            dependencyState = DependencyState.MISSING;
            demandNotified = demandedByDependent;
        }
        if (notificationsForwarded) {
            // now that the optional dependency is uninstalled, we enter automatically the up state
            switch (currentDependencyState) {
                case FAILED:
                    dependent.dependencyFailureCleared();
            }
            if (currentDependencyMissing) {
                dependent.transitiveDependencyInstalled();
            }
            dependent.immediateDependencyUp();
            if (demandNotified) {
                optionalDependency.removeDemand();
            }
        }
    }

    @Override
    public void immediateDependencyUp() {
        assert !holdsLock(this);
        final boolean notifyOptionalDependent;
        synchronized (this) {
            dependencyState = DependencyState.UP;
            notifyOptionalDependent = forwardNotifications;
        }
        if (notifyOptionalDependent) {
            dependent.immediateDependencyUp();
        }
    }

    @Override
    public void immediateDependencyDown() {
        assert !holdsLock(this);
        final boolean notifyOptionalDependent;
        synchronized (this) {
            dependencyState = DependencyState.INSTALLED;
            notifyOptionalDependent = forwardNotifications;
        }
        if (notifyOptionalDependent) {
            dependent.immediateDependencyDown();
        }
    }

    @Override
    public void dependencyFailed() {
        assert !holdsLock(this);
        final boolean notifyOptionalDependent;
        synchronized (this) {
            dependencyState = DependencyState.FAILED;
            notifyOptionalDependent = forwardNotifications;
        }
        if (notifyOptionalDependent) {
            dependent.dependencyFailed();
        }
    }

    @Override
    public void dependencyFailureCleared() {
        assert !holdsLock(this);
        final boolean notifyOptionalDependent;
        synchronized (this) {
            dependencyState = DependencyState.INSTALLED;
            notifyOptionalDependent = forwardNotifications;
        }
        if (notifyOptionalDependent) {
            dependent.dependencyFailureCleared();
        }
    }

    @Override
    public void transitiveDependencyInstalled() {
        assert !holdsLock(this);
        final boolean notifyOptionalDependent;
        synchronized (this) {
            notifyOptionalDependent = forwardNotifications;
            notifyTransitiveDependencyMissing = false;
            dependencyMissing = false;
        }
        if (notifyOptionalDependent) {
            dependent.transitiveDependencyInstalled();
        }
    }

    @Override
    public ServiceControllerImpl<?> getController() {
        return dependent.getController();
    }

    @Override
    public void transitiveDependencyUninstalled() {
        assert !holdsLock(this);
        final boolean notifyOptionalDependent;
        synchronized (this) {
            notifyOptionalDependent = forwardNotifications;
            notifyTransitiveDependencyMissing = !notifyOptionalDependent;
            dependencyMissing = true;
        }
        if (notifyOptionalDependent) {
            dependent.transitiveDependencyUninstalled();
        }
    }
}
