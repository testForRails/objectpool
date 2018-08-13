# Thread safe resources pool

Allows to add, acquire, release and remove resources from the pool.

**Under the hood :**

Backed by BlockingQueue and ConcurrencyHashMap.

The heart of the pool is a BlockingQueue which:
 * receives released or added objects and provides threads with resource on acquire method call.
 * handles concurrency 
 * blocks thread which tries to retrieve resource when queue is empty until some resource is available
 
ConcurrencyHashMap stores resource and thread id which reserved it. This helps to determine whether thread that tries to release or remove resource has authority to do it or should be blocked until resource owner thread release the resource.

Instead of method level synchronization at frequently-used methods like `add`, `remove`, `release`, `acquire` - used synchronized block by object - to avoid redundant locks.
      
      
     
 
 
 
 