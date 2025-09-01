# Dubbo DI 深度探秘：ExtensionFactory 如何从 Spring 获取 Bean

`ExtensionLoader` 中的 `private final ExtensionFactory objectFactory;` 字段之所以能从 Spring 容器中获取 bean，其奥秘在于 `ExtensionFactory` 本身也是一个**可扩展的** Dubbo SPI，而 Dubbo 提供了一个专门用于对接 Spring 的实现：`SpringExtensionFactory`。

整个过程像一个精巧的“委托链”，以下是详细的解析。

## 1. `objectFactory` 到底是什么？

在 `ExtensionLoader` 的构造函数中，`objectFactory` 字段被初始化为 `ExtensionFactory` 这个接口的**自适应扩展（Adaptive Extension）**。

```java
// ExtensionLoader.java 的构造函数中
this.objectFactory = (type == ExtensionFactory.class ? null : ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension());
```

这意味着，`this.objectFactory` 变量引用的实际上是一个在运行时动态生成的代理类，这个代理类的名字叫 `AdaptiveExtensionFactory`。

## 2. `AdaptiveExtensionFactory` 的作用：一个“总管”

`AdaptiveExtensionFactory` 的作用非常关键，它不是自己去完成工作，而是**管理和委托**给所有已知的 `ExtensionFactory` 实现。

当 `injectExtension` 方法中调用 `objectFactory.getExtension(pt, property)` 时，实际上是调用了 `AdaptiveExtensionFactory` 的 `getExtension` 方法。这个方法的逻辑如下：

```java
// AdaptiveExtensionFactory.java
public class AdaptiveExtensionFactory implements ExtensionFactory {

    // 存储了所有被发现的 ExtensionFactory 实现 (例如 SpiExtensionFactory, SpringExtensionFactory)
    private final List<ExtensionFactory> factories;

    public AdaptiveExtensionFactory() {
        // 通过 ExtensionLoader 找到所有 ExtensionFactory 的实现类，并实例化
        ExtensionLoader<ExtensionFactory> loader = ExtensionLoader.getExtensionLoader(ExtensionFactory.class);
        List<ExtensionFactory> list = new ArrayList<>();
        for (String name : loader.getSupportedExtensions()) {
            list.add(loader.getExtension(name));
        }
        factories = Collections.unmodifiableList(list);
    }

    @Override
    public <T> T getExtension(Class<T> type, String name) {
        // 【核心逻辑】遍历所有的 ExtensionFactory 实现，挨个去尝试获取 bean
        for (ExtensionFactory factory : factories) {
            T extension = factory.getExtension(type, name);
            // 如果从某个 factory 中成功获取到了 bean，就立刻返回
            if (extension != null) {
                return extension;
            }
        }
        // 如果都找不到，返回 null
        return null;
    }
}
```

**核心逻辑**：`AdaptiveExtensionFactory` 会**遍历**它所能找到的所有 `ExtensionFactory` 实现，并依次调用它们的 `getExtension` 方法。只要其中任何一个工厂返回了非 `null` 的结果，它就立刻将这个结果返回。

## 3. 两个关键的 `ExtensionFactory` 实现

Dubbo 默认提供了两个关键的 `ExtensionFactory` 实现：

1.  **`SpiExtensionFactory`**:
    *   **作用**：负责 Dubbo **内部**扩展点之间的注入。
    *   **逻辑**：当它被要求获取一个 `type` 类型的 bean 时，它会调用 `ExtensionLoader.getExtensionLoader(type).getAdaptiveExtension()`。这正是我们之前讨论的 DI 注入自适应扩展的逻辑。

2.  **`SpringExtensionFactory`**:
    *   **作用**：负责**从 Spring 容器中**查找 bean，并注入给 Dubbo 的扩展点。**这就是连接 Dubbo 和 Spring 的桥梁**。
    *   **逻辑**：
        *   `SpringExtensionFactory` 实现了 Spring 的 `ApplicationContextAware` 接口。这意味着当 Dubbo 应用与 Spring 集成时，Spring 容器在初始化 `SpringExtensionFactory` 这个 bean 的时候，会自动调用 `setApplicationContext` 方法，将 `ApplicationContext` 实例传递给它。
        *   `SpringExtensionFactory` 会**持有**这个 `ApplicationContext` 的静态引用。
        *   当它的 `getExtension` 方法被调用时，它会直接使用持有的 `ApplicationContext` 实例去查找 bean，例如 `applicationContext.getBean(type)`。

## 4. 完整的调用链

现在，我们把整个调用链串起来：

1.  **场景**：一个 Dubbo 的扩展点 `MyExtension` 需要注入一个 Spring 中定义的 `MySpringBean`。
    ```java
    public class MyExtension implements SomeSpi {
        private MySpringBean mySpringBean;
        
        public void setMySpringBean(MySpringBean mySpringBean) {
            this.mySpringBean = mySpringBean;
        }
    }
    ```
2.  `ExtensionLoader` 创建 `MyExtension` 实例后，调用 `injectExtension(myExtensionInstance)`。
3.  `injectExtension` 方法发现 `setMySpringBean` 这个 Setter，于是调用 `objectFactory.getExtension(MySpringBean.class, "mySpringBean")`。
4.  这个调用命中了 `AdaptiveExtensionFactory`。
5.  `AdaptiveExtensionFactory` 开始遍历它的工厂列表 `factories`。
    *   **首先**，调用 `SpiExtensionFactory.getExtension(...)`。由于 `MySpringBean` 不是一个 Dubbo 的 `@SPI` 接口，`SpiExtensionFactory` 找不到它，返回 `null`。
    *   **然后**，调用 `SpringExtensionFactory.getExtension(...)`。
6.  `SpringExtensionFactory` 使用它持有的 `ApplicationContext`，调用 `applicationContext.getBean(MySpringBean.class)`。
7.  Spring 容器成功找到了名为 `mySpringBean` 的 bean，并将其返回。
8.  `SpringExtensionFactory` 将这个 bean 实例返回给 `AdaptiveExtensionFactory`。
9.  `AdaptiveExtensionFactory` 发现获取到了非 `null` 的结果，于是立即将这个 bean 实例返回给 `injectExtension` 方法。
10. `injectExtension` 方法通过反射调用 `myExtensionInstance.setMySpringBean(mySpringBeanInstance)`，注入成功。

**总结：**

`objectFactory` 字段本身只是一个“总管”代理。它通过**委派**给一个实现了 `ExtensionFactory` 接口的**实现类列表**来工作。当应用整合了 Spring 时，这个列表中就会包含 `SpringExtensionFactory`，它作为一个**桥梁**，利用 Spring 的 `ApplicationContextAware` 机制拿到了 Spring 上下文，从而实现了从 Spring 容器中查找并返回 bean 的能力。
