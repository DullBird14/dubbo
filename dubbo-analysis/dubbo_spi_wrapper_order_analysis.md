### 为什么 Dubbo Wrapper 的包装顺序是固定的？代码哪里保证了这一点？

`ProtocolListenerWrapper` -> `ProtocolFilterWrapper` -> `DubboProtocol` 这个顺序是固定且由框架保证的。如果顺序颠倒（例如 `Filter` 在 `Listener` 外层），那么 `Listener` 将无法监听到由 `Filter` 中断的调用，这会破坏框架设计的逻辑。

这种固定顺序的保证，其核心在于 **Dubbo `ExtensionLoader` 加载和应用 Wrapper 的机制**。

具体来说，它由以下两点保证：

#### 1. Wrapper 的识别与收集

当 `ExtensionLoader` 为一个接口（如 `org.apache.dubbo.rpc.Protocol`）创建扩展实例时，它会扫描该接口的所有已注册的扩展实现类。如果一个类拥有一个以该接口为唯一参数的**拷贝构造函数**（Copy Constructor），那么这个类就被识别为一个 `Wrapper`。

例如，`ProtocolFilterWrapper` 有一个构造函数：
```java
public ProtocolFilterWrapper(Protocol protocol) {
    this.protocol = protocol;
}
```
这符合了 `Wrapper` 的定义，所以 `ExtensionLoader` 会把它收集到一个 `List<Class<?>>` (wrapper列表) 中。`ProtocolListenerWrapper` 同理。

#### 2. Wrapper 的应用顺序：由配置文件中的 Key 字母顺序决定

这是保证顺序的关键所在。`ExtensionLoader` 在应用 `Wrapper` 列表对扩展实例进行包装时，这个 `Wrapper` 列表**不是无序的**。它的顺序取决于这些 `Wrapper` 在 `META-INF/dubbo/` 配置文件中定义的 **`key` (名称) 的字母顺序**。

我们来看一下 Dubbo 框架内部为 `Protocol` 接口定义的配置文件 `META-INF/dubbo/org.apache.dubbo.rpc.Protocol`：

```properties
# 这是dubbo-rpc-api.jar中的配置文件内容
filter=org.apache.dubbo.rpc.protocol.ProtocolFilterWrapper
listener=org.apache.dubbo.rpc.protocol.ProtocolListenerWrapper
mock=org.apache.dubbo.rpc.support.MockProtocol
# ... 其他protocol实现
```

`ExtensionLoader` 在加载这个文件时，会把这些 `key=value` 的配置存入一个 **有序的Map**（内部实现类似 `TreeMap`，按Key排序）。

因此，`Wrapper` 的 `key` 决定了它们在 `Wrapper` 列表中的顺序：
- `filter` 的首字母是 `f`
- `listener` 的首字母是 `l`

按照字母顺序，`filter` 排在 `listener` 前面。

当 `ExtensionLoader` 循环应用这些 `Wrapper` 时，代码逻辑大致如下：

```java
// ExtensionLoader.java 中的伪代码
T instance = new DubboProtocol(); // 1. 创建具体实现

// wrapperClasses 列表按 key 的字母顺序排序为 [ProtocolFilterWrapper.class, ProtocolListenerWrapper.class]
List<Class<?>> wrapperClasses = getCachedWrapperClasses(); 

// 2. 按顺序循环应用 Wrapper
for (Class<?> wrapperClass : wrapperClasses) {
    // 第一次循环: instance = new ProtocolFilterWrapper(instance);
    // 第二次循环: instance = new ProtocolListenerWrapper(instance);
    instance = wrapperClass.getConstructor(interfaceType).newInstance(instance);
}

// 3. 返回最终被包装的实例
return instance;
```

最终生成的对象结构是：
`new ProtocolListenerWrapper(new ProtocolFilterWrapper(new DubboProtocol(...)))`

所以，当 `export()` 方法被调用时，调用链就是：
`ProtocolListenerWrapper` -> `ProtocolFilterWrapper` -> `DubboProtocol`

**总结**：**Wrapper 的包装顺序是固定的，它由 `META-INF/dubbo/` 配置文件中为 Wrapper 类指定的 `key` 的字母顺序决定。** 这是 Dubbo 框架提供的一种约定，用以保证像 Filter、Listener 这类具有顺序依赖的横切关注点能够被正确、稳定地应用。
