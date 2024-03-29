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
package com.alibaba.dubbo.remoting.zookeeper;

import com.alibaba.dubbo.common.URL;

import java.util.List;

public interface ZookeeperClient {
    /**
     * 创建节点
     * @param path
     * @param ephemeral 是否创建临时节点
     */
    void create(String path, boolean ephemeral);

    /**
     * 删除节点
     * @param path
     */
    void delete(String path);

    /**
     * 获取子节点
     * @param path
     * @return
     */
    List<String> getChildren(String path);

    /**
     * 添加子节点监听器
     * @param path
     * @param listener
     * @return
     */
    List<String> addChildListener(String path, ChildListener listener);

    /**
     * 移除子节点监听器
     * @param path
     * @param listener
     */
    void removeChildListener(String path, ChildListener listener);

    /**
     * 添加状态监听器
     * @param listener
     */
    void addStateListener(StateListener listener);

    /**
     * 移除状态监听器
     * @param listener
     */
    void removeStateListener(StateListener listener);

    /**
     * 获取状态
     * @return
     */
    boolean isConnected();

    /**
     * 关闭链接
     */
    void close();

    /**
     * 获取url
     * @return
     */
    URL getUrl();

}
