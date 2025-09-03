package ndiplayer.oto.utils;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pool de objetos thread-safe para evitar allocaciones constantes
 * Optimizado para alto rendimiento en dispositivos de bajo recursos
 */
public class ObjectPool<T> {
    private final ConcurrentLinkedQueue<T> pool;
    private final Supplier<T> factory;
    private final Consumer<T> resetFunction;
    private final AtomicInteger size;
    private final int maxSize;
    
    public ObjectPool(Supplier<T> factory, Consumer<T> resetFunction, int maxSize) {
        this.pool = new ConcurrentLinkedQueue<>();
        this.factory = factory;
        this.resetFunction = resetFunction;
        this.size = new AtomicInteger(0);
        this.maxSize = maxSize;
        
        // Pre-llenar el pool con algunos objetos
        int initialSize = Math.min(maxSize / 2, 3);
        for (int i = 0; i < initialSize; i++) {
            pool.offer(factory.get());
            size.incrementAndGet();
        }
    }
    
    /**
     * Obtiene un objeto del pool o crea uno nuevo si está vacío
     */
    public T acquire() {
        T object = pool.poll();
        if (object != null) {
            size.decrementAndGet();
            return object;
        }
        
        // Pool vacío, crear nuevo objeto
        return factory.get();
    }
    
    /**
     * Devuelve un objeto al pool
     */
    public void release(T object) {
        if (object == null) return;
        
        // Reset del objeto si hay función de reset
        if (resetFunction != null) {
            try {
                resetFunction.accept(object);
            } catch (Exception e) {
                // Si falla el reset, no devolver al pool
                return;
            }
        }
        
        // Solo agregar si no hemos excedido el tamaño máximo
        if (size.get() < maxSize) {
            pool.offer(object);
            size.incrementAndGet();
        }
        // Si el pool está lleno, dejar que el GC maneje el objeto
    }
    
    /**
     * Limpia el pool
     */
    public void clear() {
        pool.clear();
        size.set(0);
    }
    
    /**
     * Obtiene el tamaño actual del pool
     */
    public int size() {
        return size.get();
    }
    
    /**
     * Verifica si el pool está vacío
     */
    public boolean isEmpty() {
        return pool.isEmpty();
    }
}
