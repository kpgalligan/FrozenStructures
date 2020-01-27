import kotlinx.atomicfu.AtomicRef
import kotlin.native.concurrent.DetachedObjectGraph
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.attach

// Used when the object is simply read/copied.
internal inline fun <reified T, TReturn> AtomicRef<DetachedObjectGraph<T>?>.unsafeAccess(crossinline block: (T) -> TReturn): TReturn {
    var graph: DetachedObjectGraph<T>? = null

    // Keep going until we get the graph.
    // This should be replaced with a Lock.
    while (graph == null) graph = getAndSet(null)

    var result: Result<TReturn>? = null
    val newGraph = DetachedObjectGraph(TransferMode.UNSAFE) {
        val map = graph.attach()
        result = runCatching { block(map) }
        map
    }
    check(compareAndSet(null, newGraph)) { "UH OH!" }
    val actualResult = checkNotNull(result) { "Uh OH!" }
    return actualResult.getOrThrow()
}
