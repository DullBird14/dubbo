# Dubbo `protocol.export` 深度解析

这篇文档深入剖析 `protocol.export(wrapperInvoker)` 这行代码的内部逻辑、类依赖关系和调用流程。

这是一个典型的**装饰器模式**和**自适应扩展机制**协同工作的经典范例。调用看似简单，实则经过了层层包装和动态选择，最终才完成网络服务的暴露。

### 调用依赖与流转关系

调用并不是直接从 `protocol` 变量一步到达最终的 `DubboProtocol`。`protocol` 变量本身是 `Protocol` 接口的自适应扩展实例 (`Protocol$Adaptive`)。它会根据 `Invoker` 的 `URL` 中的协议头（如 `dubbo://`）来动态选择真正的实现。而这个实现又被多个 `Wrapper` 类层层包装。

**调用流转顺序如下：**

1.  **`Protocol$Adaptive` (入口)**
    *   **职责**: 动态决策者。它不是一个具体的协议实现，而是一个在内存中动态生成的代理。
    *   **逻辑**:
        1.  从传入的 `wrapperInvoker` 中获取 `URL` 对象。
        2.  解析 `URL` 的协议头（`url.getProtocol()`），例如得到字符串 `"dubbo"`。
        3.  使用这个字符串作为 `name`，调用 `ExtensionLoader.getExtension("dubbo")` 来获取真正的协议扩展实例。
    *   **流向**: 将 `export` 调用委托给 `ExtensionLoader` 返回的实例。

2.  **`ProtocolListenerWrapper` (第一层包装)**
    *   **职责**: 监听器调用者。它是一个装饰器，为服务暴露和取消暴露的生命周期添加监听事件。
    *   **逻辑**:
        1.  在调用下一层 `export` **之前**，不执行任何操作。
        2.  调用被包装的 `protocol` 实例（即 `ProtocolFilterWrapper`）的 `export` 方法，得到一个 `Exporter`。
        3.  创建一个 `ListenerExporterWrapper`，该 `Wrapper` 持有上一步得到的 `Exporter` 和所有激活的 `ExporterListener`。
        4.  遍历所有 `ExporterListener`，并调用它们的 `exported()` 方法，通知它们服务已暴露。
        5.  返回 `ListenerExporterWrapper`。
    *   **流向**: 将 `export` 调用委托给下一层包装 `ProtocolFilterWrapper`。

3.  **`ProtocolFilterWrapper` (第二层包装)**
    *   **职责**: 过滤器链组织者。这是Dubbo实现`Filter`机制（AOP）的核心，也是最重要的一层包装。
    *   **逻辑**:
        1.  从 `URL` 中获取所有被激活的 `Filter`（通过 `@Activate` 注解和 `service.filter` 配置）。
        2.  **关键步骤**: 将这些 `Filter` 构建成一个 `Invoker` 调用链。它会创建一个新的 `Invoker`，这个 `Invoker` 的 `invoke` 方法会先执行第一个 `Filter` 的逻辑，然后调用下一个 `Filter` 包装的 `Invoker`，以此类推，直到调用链的末端——原始的 `wrapperInvoker`。
        3.  将这个**被过滤器链包装过的、全新的 `Invoker`** 传递给下一层的 `protocol`（即 `DubboProtocol`）去 `export`。
    *   **流向**: 将 `export` 调用委托给最终的实现 `DubboProtocol`，但传递的 `Invoker` 已被“改造”。

4.  **`DubboProtocol` (最终实现)**
    *   **职责**: 真正的网络服务暴露者。
    *   **逻辑**:
        1.  调用 `openServer()` 方法，根据 `URL` 的 IP 和端口信息，启动一个 Netty Server（如果该端口的 Server 尚未启动）。
        2.  将**包含了过滤器链的 `Invoker`** 存储在一个 `exporterMap` (一个 `ConcurrentHashMap`) 中。Map 的 `key` 是服务的唯一标识（`serviceKey`，如 `group/interfaceName:version:port`）。
        3.  当 Netty Server 收到客户端请求时，会根据请求中的 `serviceKey` 从 `exporterMap` 中找到对应的 `Invoker`，然后调用其 `invoke` 方法。此时，请求就会自然地流经 `ProtocolFilterWrapper` 构建的整个过滤器链。
        4.  返回一个 `DubboExporter` 对象，该对象持有 `serviceKey` 和 `exporterMap` 的引用，以便后续可以通过它来取消暴露（`unexport`）。
    *   **流向**: 调用结束，返回 `DubboExporter`。

这个 `DubboExporter` 会被 `ProtocolListenerWrapper` 包装成 `ListenerExporterWrapper`，最终返回给最上层的调用者 `ServiceConfig`。

### 关键代码示例

#### 1. `ProtocolFilterWrapper`：构建 `Invoker` 链

```java
// ProtocolFilterWrapper.java (简化后)
public class ProtocolFilterWrapper implements Protocol {
    private final Protocol protocol;

    public ProtocolFilterWrapper(Protocol protocol) {
        this.protocol = protocol;
    }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        // 1. 获取所有激活的 Filter
        List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class).getActivateExtension(invoker.getUrl(), Constants.PROVIDER);

        // 2. 构建 Invoker 调用链
        Invoker<T> last = invoker;
        for (int i = filters.size() - 1; i >= 0; i--) {
            final Filter filter = filters.get(i);
            final Invoker<T> next = last;
            // 创建一个新的匿名 Invoker，它会先执行 filter 逻辑，再调用下一个 Invoker
            last = new Invoker<T>() {
                // ... 省略其他方法
                @Override
                public Result invoke(Invocation invocation) throws RpcException {
                    return filter.invoke(next, invocation);
                }
            };
        }

        // 3. 用被 Filter 链包装后的 Invoker，去调用下一层的 export
        return protocol.export(last);
    }
}
```
**解读**: `protocol.export(last)` 中的 `last` 已经不是原始的 `invoker` 了，它变成了 `Filter1(Filter2(Filter3(...(original_invoker)...)))` 这样的调用结构。

#### 2. `DubboProtocol`：启动服务器并保存 `Invoker`

```java
// DubboProtocol.java (简化后)
public class DubboProtocol extends AbstractProtocol {

    // 存储所有被暴露的 Invoker
    private final Map<String, Exporter<?>> exporterMap = new ConcurrentHashMap<>();

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        URL url = invoker.getUrl();
        String key = serviceKey(url); // 生成 serviceKey

        // 创建一个 DubboExporter，并放入 Map 中
        DubboExporter<T> exporter = new DubboExporter<T>(invoker, key, exporterMap);
        exporterMap.put(key, exporter);

        // 启动网络服务器
        openServer(url);

        return exporter;
    }

    private void openServer(URL url) {
        String key = url.getAddress(); // ip:port
        // double-checked locking
        ExchangeServer server = serverMap.get(key);
        if (server == null) {
            synchronized (this) {
                server = serverMap.get(key);
                if (server == null) {
                    // 创建并启动 Netty Server，并设置请求处理器(RequestHandler)
                    // 这个处理器会从 exporterMap 中查找 Invoker 并调用
                    serverMap.put(key, createServer(url));
                }
            }
        }
    }
}
```
**解读**: `DubboProtocol` 的核心工作就是**启动网络服务**和**管理 `Invoker` 的路由表 (`exporterMap`)**。它并不知道 `Filter` 的存在，它只负责接收一个 `Invoker`，并在收到网络请求时调用它。`Filter` 的功能已经被 `ProtocolFilterWrapper` 透明地织入到了 `Invoker` 内部。
