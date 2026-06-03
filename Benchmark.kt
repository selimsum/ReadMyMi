import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.measureTimeMillis

fun main() {
    val timestamps = LongArray(100000) { System.currentTimeMillis() - it * 1000L }
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // Baseline
    var time1 = measureTimeMillis {
        val csvBuilder = StringBuilder()
        for (ts in timestamps) {
            val dateTime = sdf.format(Date(ts))
            csvBuilder.append(dateTime).append("\n")
        }
    }

    // Optimized
    var time2 = measureTimeMillis {
        val csvBuilder = StringBuilder()
        val dateObj = Date()
        for (ts in timestamps) {
            dateObj.time = ts
            val dateTime = sdf.format(dateObj)
            csvBuilder.append(dateTime).append("\n")
        }
    }

    println("Baseline: $time1 ms")
    println("Optimized: $time2 ms")
}
