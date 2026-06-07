1. **Analyze the performance baseline (Done)**: I have reviewed the `SensorDaoBenchmarkTest.kt` inside the repository. It measures batch insert time vs individual insert time. Although I couldn't run `connectedAndroidTest` due to a lack of devices on the CI, the logic from the Android benchmark indicates batch inserts are an optimization opportunity for `SensorDao`. We will utilize the batch logic for our performance improvements.

2. **Implement the optimization in `SensorForegroundService.kt`**:
   - Define a buffer (e.g. `private val dbSaveBuffer = mutableMapOf<String, SensorEntity>()`).
   - Also, define `private var bufferJob: Job? = null`
   - Modify `throttleSaveToDb` to not immediately launch a new coroutine on every single request that passes the throttle check. Instead, it adds the `SensorEntity` to `dbSaveBuffer`.
   - If the buffer reaches a certain size (e.g. 50), or periodically via a coroutine flow/job, save all buffered entities at once using `database.sensorDao().insertAll(dbSaveBuffer.values.toList())` inside an IO dispatch.
   - For `throttleSaveToDb`: We can buffer up to N items or insert them after a time interval (e.g., using a separate flush job).
   - Actually, a simpler way is maintaining `private val insertBuffer = mutableListOf<SensorEntity>()` and a lock (e.g., `Mutex` or `synchronized`). However, since `throttleSaveToDb` is called frequently, and since this is a service, periodic background insertion via `serviceScope` is very efficient.
   - Wait, `throttleSaveToDb` already throttles by macAddress. It says `if (now - lastSave > 60000)`. So it only inserts one record per device every minute.
   - If a user has 10 devices, that's 10 inserts per minute. The prompt explicitly says: "Frequent Database Inserts with Separate Transactions... High confidence as individual DB inserts per record cause unnecessary overhead. Refactoring to batch inserts or maintaining a buffer before inserting would improve performance."
   - Let's create a buffering mechanism in `SensorForegroundService`:
     ```kotlin
     private val dbSaveBuffer = java.util.concurrent.ConcurrentLinkedQueue<SensorEntity>()
     private var lastDbBatchSaveTime = 0L

     private fun throttleSaveToDb(it: SensorData) {
         val now = System.currentTimeMillis()
         val lastSave = lastDbSaveMap[it.macAddress] ?: 0L
         if (now - lastSave > 60000) {
             val entity = SensorEntity(
                 macAddress = it.macAddress,
                 temperature = it.temperature.toFloat(),
                 humidity = it.humidity.toInt(),
                 battery = it.battery,
                 timestamp = it.timestamp
             )
             dbSaveBuffer.add(entity)
             lastDbSaveMap[it.macAddress] = now

             if (dbSaveBuffer.size >= 10 || now - lastDbBatchSaveTime > 60000) {
                 flushDbBuffer()
             }
         }
     }

     private fun flushDbBuffer() {
         if (dbSaveBuffer.isEmpty()) return
         val entitiesToSave = mutableListOf<SensorEntity>()
         while (dbSaveBuffer.isNotEmpty()) {
             dbSaveBuffer.poll()?.let { entitiesToSave.add(it) }
         }
         if (entitiesToSave.isNotEmpty()) {
             lastDbBatchSaveTime = System.currentTimeMillis()
             serviceScope.launch(Dispatchers.IO) {
                 try {
                     database.sensorDao().insertAll(entitiesToSave)
                 } catch (e: Exception) {
                     AppLogger.log("SensorService", "DB Save Error: ${e.message}")
                 }
             }
         }
     }
     ```
   - Also flush on `onDestroy`.

3. **Verify the logic**: Make sure no compilation errors exist and lint checks pass.
4. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.**
5. **Submit a PR**: Provide clear info on the performance benefits.
