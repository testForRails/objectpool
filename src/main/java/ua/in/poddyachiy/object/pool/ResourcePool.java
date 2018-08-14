package ua.in.poddyachiy.object.pool;

import ua.in.poddyachiy.object.pool.exception.PoolIsNotOpenException;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by sergii on 10.08.18.
 */
public class ResourcePool<R> {
    /**
     * Pool of resources. Arguments used in constructor of BlockingQueue:
     *      size - pool size, if it is exceeded - thread that tries to add new resource will be blocked unit pool will gets some free space.
     * Currently limit is set to Integer.MAX_VALUE
     *      fair - first thread requested the resource - will get it first
     */
    private BlockingQueue<R> pool = new ArrayBlockingQueue<R>(Integer.MAX_VALUE, true);
    /**
     * Map<Resource,Thread id (Long) that reserved the resource> - map of locked objects and their owner thread id.
     */
    private Map<R, Long> locked = new ConcurrentHashMap();
    /**
     * Saves state - whether pool is open
     */
    private volatile boolean isOpen;


    /**
     * Acquires available resource from the pool.
     * If pool is empty - thread will be blocked until some resource will appear in the poll.
     * Since pool is BlockingQueue created with "fair" argument in constructor - first thread called this method - will get resource first.
     *
     * @return R - resource
     * @throws InterruptedException, PoolIsNotOpenException
     */
    public R acquire() throws InterruptedException, PoolIsNotOpenException {
        validateIsOpen();
        R acquired = pool.take();
        synchronized (acquired) {
            locked.put(acquired, Thread.currentThread().getId());
        }

        return acquired;
    }

    /**
     * Tries to acquire available resource from the pool during given time period.
     * Returns null after timeout.
     * Since pool is BlockingQueue created with "fair" argument in constructor - first thread called this method - will get resource first.
     *
     * @return R - resource
     * @throws InterruptedException, PoolIsNotOpenException
     */
    public R acquire(long timeout, TimeUnit unit) throws InterruptedException, PoolIsNotOpenException {
        validateIsOpen();
        R acquired = pool.poll(timeout, unit);

        // in case of timeout - poll can return null
        if (acquired != null) {
            synchronized (acquired) {
                locked.put(acquired, Thread.currentThread().getId());
            }
        }

        return acquired;
    }

    /**
     * Tries to add resource to the pool.
     *   Returns:
     *   true - if resource is successfully added
     *   false if:
     *      * Resource is already in use
     *      * Pool is full
     *
     * @param resource
     * @return boolean
     * @throws InterruptedException, PoolIsNotOpenException
     */
    public boolean add(R resource) throws InterruptedException, PoolIsNotOpenException {
        synchronized (resource) {
            validateIsOpen();

            if (locked.containsKey(resource)) {
                return false;
            }
            return pool.offer(resource);
        }
    }

    /**
     * Releases reserved by thread resource and puts it back into the poll.
     * Only thread that acquired resource can release it.
     *
     * @param resource
     * @throws InterruptedException, PoolIsNotOpenException
     */
    public void release(R resource) throws InterruptedException, PoolIsNotOpenException {
        synchronized (resource) {
            validateIsOpen();
            // if current thread locked the resource -
            if (unlock(resource, false)) {
                add(resource);
            }
        }
    }

    /**
     * Removes a resource from the pool.
     *
     * If resource is in use and current thread is not one which acquired the resource -
     * method blocks current thread until resource is released.
     * Returns true if succeed and false if resource doesn't exist in pool
     *
     * @param resource
     * @return
     * @throws InterruptedException, PoolIsNotOpenException
     */
    public boolean remove(R resource) throws InterruptedException, PoolIsNotOpenException {
        return remove(resource, true);
    }

    /**
     * Tries to remove the resource from the pool.
     * Returns true if succeed and false if thread is not one which acquired it or resource doesn't exist in pool
     * @param resource
     * @return
     * @throws InterruptedException, PoolIsNotOpenException
     */
    public boolean removeNow(R resource) throws InterruptedException, PoolIsNotOpenException {
            return remove(resource, false);
    }

    private boolean remove(R resource, boolean waitUntilReleased) throws InterruptedException, PoolIsNotOpenException {
        // lock by specific object - instead of blocking whole method
        synchronized (resource) {
            validateIsOpen();
            if (!unlock(resource, waitUntilReleased)) {
                return false;
            }
            return pool.remove(resource);
        }
    }

    public synchronized void open() {
        isOpen = true;
    }

    /**
     * Closes the pool and removes all used resources.
     * If resource is in use - blocks current thread until resource is released.
     *
     * @throws InterruptedException, PoolIsNotOpenException
     */
    public synchronized void close() throws InterruptedException, PoolIsNotOpenException {
        validateIsOpen();
        isOpen = false;
        pool.clear();

        for (R aResource : locked.keySet()) {
            remove(aResource);
        }
    }

    /**
     * Closes the pool and clears all resource regardless whether they are in use or not
     *
     * @throws InterruptedException, PoolIsNotOpenException
     */
    public synchronized void closeNow() throws InterruptedException, PoolIsNotOpenException {
        validateIsOpen();
        isOpen = false;
        pool.clear();
        locked.clear();
    }

    /**
     * Removes resource from locked list - if current thread is one which acquired the resource.
     * @param resource - resource to unlock
     * @param waitUntilReleased - block current thread until resource owner thread release it
     * @return
     * @throws InterruptedException, PoolIsNotOpenException
     */
    private boolean unlock(R resource, boolean waitUntilReleased) throws InterruptedException, PoolIsNotOpenException {
        // object was not locked
        if (!locked.containsKey(resource)) {
            return false;
        }
        // get process which reserved the resource
        Long ownerThreadId = locked.get(resource);
        // if current thread is resource owner - no need to unlock. But call notifyAll - to notify other threads that resource released
        if (ownerThreadId.equals(Thread.currentThread().getId())) {
            synchronized (resource) {
                resource.notifyAll();
            }
            return true;
        }
        if (waitUntilReleased) {
            // if another thread acquired the resource - wait until that thread release it ()
            synchronized (resource) {
                resource.wait();
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean isOpen(){
        return isOpen;
    }

    public  void validateIsOpen(){
        if(!isOpen){
          throw new PoolIsNotOpenException();
        }
    }


}


