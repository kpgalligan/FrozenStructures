import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.StableRef
import kotlin.native.concurrent.*

class DetachedMutableMap<K : Any, V : Any> : MutableMap<K, V> {
    // Underlying map is kept in a detached graph, but keys and values are kept indirectly.
    private val realMap: AtomicRef<DetachedObjectGraph<MutableMap<Wrapper<K>, Wrapper<V>>>?>

//    constructor() : this(HashMap.INITIAL_CAPACITY)
//    constructor(initialCapacity: Int) : this(0)
//    constructor(original: Map<out K, V>) : this(original.size) { putAll(original) }

    init {
        val objectGraph = DetachedObjectGraph(TransferMode.SAFE) {
            mutableMapOf<Wrapper<K>, Wrapper<V>>()
        }
        realMap = atomic(objectGraph)

        freeze() // Might optimize this away later.
    }

    override val size: Int get() = realMap.unsafeAccess { it.size }  // Cache this?

    override fun isEmpty(): Boolean = realMap.unsafeAccess { it.isEmpty() }

    override fun containsKey(key: K): Boolean {
        return realMap.unsafeAccess { map ->
            withWrapper(key) { wKey ->
                map.containsKey(wKey)
            }
        }
    }

    override fun containsValue(value: V): Boolean {
        return realMap.unsafeAccess { map ->
            // withWrapper(value) { wValue ->
            //     map.containsValue(wValue)
            // }
            map.values.any { it.get() == value }
        }
    }

    override fun get(key: K): V? {
        return realMap.unsafeAccess { map ->
            withWrapper(key) { wKey ->
                val valueWrapper = map[wKey]
                // Can be returned since it's being pulled out of a StableRef
                valueWrapper?.get()
            }
        }
    }

    override fun remove(key: K): V? {
        return realMap.unsafeAccess { map ->
            withWrapper(key) { wKey ->
                // FIXME: Old key is leaked here.
                val valueWrapper = map.remove(wKey)
                // Can be returned since it's being pulled out of a StableRef
                valueWrapper?.consume()
            }
        }
    }

    override fun put(key: K, value: V): V? {
        key.freeze() // I think the key should only be frozen if it's new to the map.
        value.freeze()
        return realMap.unsafeAccess { map ->
            val wKey = Wrapper(key)
            val wValue = Wrapper(value)

            // FIXME: Old xor new key is leaked here.
            val valueWrapper = map.put(wKey, wValue)
            // Can be returned since it's being pulled out of a StableRef
            valueWrapper?.consume()
        }
    }

    override fun putAll(from: Map<out K, V>) {
        // Optimize for when `from` is an instance of this class.
        realMap.unsafeAccess { map ->
            for (entry in from) {
                val wKey = Wrapper(entry.key)
                val wValue = Wrapper(entry.value)

                // FIXME: Old xor new key is leaked here.
                val valueWrapper = map.put(wKey, wValue)
                valueWrapper?.consume()
            }
        }
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() {
        // TODO("This complicates locking.")
        return realMap.unsafeAccess { map ->
            map.entries.mapTo(LinkedHashSet(map.size)) { entry ->
                Entry(entry.key.get(), entry.value.get())
            }
        }
    }
    override val keys: MutableSet<K>
        get() {
            // TODO("This complicates locking.")
            return realMap.unsafeAccess { map ->
                map.keys.mapTo(LinkedHashSet(map.size)) { it.get() }
            }
        }
    override val values: MutableCollection<V>
        get() {
            // TODO("This complicates locking.")
            return realMap.unsafeAccess { map ->
                map.values.mapTo(ArrayList(map.size)) { it.get() }
            }
        }

    override fun clear() {
        realMap.unsafeAccess { map ->
            // Release StableRef to entries.
            map.keys.forEach { it.dispose() }
            map.values.forEach { it.dispose() }

            // Actually clear map.
            map.clear()
        }
    }

    // Could be inline.
    private class Wrapper<T: Any>(obj: T) {
        private val ref: StableRef<T>

        init {
            check(obj.isFrozen)
            ref = StableRef.create(obj)
        }

        fun get(): T = ref.get()
        fun consume(): T {
            val value = ref.get()
            ref.dispose()
            return value
        }
        fun dispose() {
            ref.dispose()
        }

        override fun equals(other: Any?): Boolean {
            return other is Wrapper<*> && get() == other.get()
        }

        override fun hashCode(): Int = get().hashCode()
    }
    private inline fun <T, TObj : Any> withWrapper(obj: TObj, block: (Wrapper<TObj>) -> T): T {
        val temp = Wrapper(obj)
        return try {
            block(temp)
        } finally {
            temp.consume()
        }
    }

    class Entry<K, V>(override val key: K, override val value: V) : MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V {
            TODO("not implemented")
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null) return false
            if (this::class != other::class) return false

            other as Entry<*, *>

            if (key != other.key) return false
            if (value != other.value) return false

            return true
        }

        override fun hashCode(): Int {
            var result = key?.hashCode() ?: 0
            result = 31 * result + (value?.hashCode() ?: 0)
            return result
        }
    }
}
