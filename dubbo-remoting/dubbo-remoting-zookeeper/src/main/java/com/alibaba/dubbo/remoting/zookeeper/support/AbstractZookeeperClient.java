/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.remoting.zookeeper.support;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConcurrentHashSet;
import com.alibaba.dubbo.remoting.zookeeper.ChildListener;
import com.alibaba.dubbo.remoting.zookeeper.StateListener;
import com.alibaba.dubbo.remoting.zookeeper.ZookeeperClient;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 抽象功能代码
 * 此处的 <TargetChildListener> 和 <T> 是一样的
 * todo 仔细理解 <TargetChildListener>
 * @param <TargetChildListener>
 */
public abstract class AbstractZookeeperClient<TargetChildListener> implements ZookeeperClient {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractZookeeperClient.class);
    /**
     * url
     */
    private final URL url;
    /**
     * 状态监听器集合
     */
    private final Set<StateListener> stateListeners = new CopyOnWriteArraySet<StateListener>();
    /**
     * todo 不理解
     * ChildListener 集合
     *
     * key1：节点路径
     * key2：ChildListener 对象
     * value ：监听器具体对象。不同 Zookeeper 客户端，实现会不同。
     */
    private final ConcurrentMap<String, ConcurrentMap<ChildListener, TargetChildListener>> childListeners = new ConcurrentHashMap<String, ConcurrentMap<ChildListener, TargetChildListener>>();
    /**
     * 是否关闭
     */
    private volatile boolean closed = false;
    /**
     * 永久存在的路径的集合
     */
    private final Set<String>  persistentExistNodePath = new ConcurrentHashSet<String>();

    public AbstractZookeeperClient(URL url) {
        this.url = url;
    }

    @Override
    public URL getUrl() {
        return url;
    }


    @Override
    public void delete(String path){
        //never mind if ephemeral
        // 从持久化集合中删除
        persistentExistNodePath.remove(path);
        deletePath(path);
    }


    @Override
    public void create(String path, boolean ephemeral) {
        if (!ephemeral) {
            // 如果不是临时节点
            if(persistentExistNodePath.contains(path)){
                return;
            }
            if (checkExists(path)) {
                // 路径已经存在，由不同的客户端代码自己去判断
                // 添加到持久化的集合中
                persistentExistNodePath.add(path);
                return;
            }
        }
        int i = path.lastIndexOf('/');
        if (i > 0) {
            // 如果包含多级路径，递归处理。并且创建的父级，都是持久的路径
            create(path.substring(0, i), false);
        }
        if (ephemeral) {
            // 创建临时节点
            createEphemeral(path);
        } else {
            // 创建永久的节点
            createPersistent(path);
            persistentExistNodePath.add(path);

        }
    }

    /**
     * 状态监听器的添加。委托给stateListeners集合
     * @param listener
     */
    @Override
    public void addStateListener(StateListener listener) {
        stateListeners.add(listener);
    }
    /**
     * 状态监听器的删除。委托给stateListeners集合
     * @param listener
     */
    @Override
    public void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }

    public Set<StateListener> getSessionListeners() {
        return stateListeners;
    }

    @Override
    public List<String> addChildListener(String path, final ChildListener listener) {
        // 获取 路径原来的子节点监听器
        ConcurrentMap<ChildListener, TargetChildListener> listeners = childListeners.get(path);
        if (listeners == null) {
            // 如果为null，就放置并且获取
            childListeners.putIfAbsent(path, new ConcurrentHashMap<ChildListener, TargetChildListener>());
            listeners = childListeners.get(path);
        }
        // 获取这个listenter的value，如果不存在就添加并且获取
        TargetChildListener targetListener = listeners.get(listener);
        if (targetListener == null) {
            listeners.putIfAbsent(listener, createTargetChildListener(path, listener));
            targetListener = listeners.get(listener);
        }
        // 如果已经存在， 通过指定的客户端添加
        return addTargetChildListener(path, targetListener);
    }

    @Override
    public void removeChildListener(String path, ChildListener listener) {
        ConcurrentMap<ChildListener, TargetChildListener> listeners = childListeners.get(path);
        if (listeners != null) {
            TargetChildListener targetListener = listeners.remove(listener);
            if (targetListener != null) {
                removeTargetChildListener(path, targetListener);
            }
        }
    }

    /**
     * 状态改变触发监听器
     * @param state
     */
    protected void stateChanged(int state) {
        for (StateListener sessionListener : getSessionListeners()) {
            sessionListener.stateChanged(state);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            doClose();
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
    }

    protected abstract void doClose();

    protected abstract void createPersistent(String path);

    protected abstract void createEphemeral(String path);

    protected abstract boolean checkExists(String path);

    protected abstract TargetChildListener createTargetChildListener(String path, ChildListener listener);

    protected abstract List<String> addTargetChildListener(String path, TargetChildListener listener);

    protected abstract void removeTargetChildListener(String path, TargetChildListener listener);

    /**
     * we invoke the zookeeper client to delete the node
     * @param path the node path
     */
    protected abstract void deletePath(String path);
}
