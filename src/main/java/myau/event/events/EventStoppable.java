package myau.event.events;

import lombok.Getter;

/**
 * The most basic form of an stoppable Event.
 * Stoppable events are called seperate from other events and the calling of methods is stopped
 * as soon as the EventStoppable is stopped.
 *
 * @author DarkMagician6
 * @since 26-9-13
 */
@Getter
public abstract class EventStoppable implements Event {
    /**
     * -- GETTER --
     * Checks the stopped boolean.
     *
     */
    private boolean stopped;

    /**
     * No need for the constructor to be public.
     */
    protected EventStoppable() {
    }

}
