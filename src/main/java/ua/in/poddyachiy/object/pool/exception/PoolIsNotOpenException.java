package ua.in.poddyachiy.object.pool.exception;

/**
 * Created by sergii on 14.08.18.
 */
public class PoolIsNotOpenException extends RuntimeException {

    public PoolIsNotOpenException() {
        super("Pool is not open");
    }
}
