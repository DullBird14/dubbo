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
package com.alibaba.dubbo.rpc.cluster.loadbalance;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;

import java.util.List;
import java.util.Random;

/**
 * random load balance.
 *  随机选择负载均衡
 */
public class RandomLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "random";

    private final Random random = new Random();

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Number of invokers
        int length = invokers.size();
        // The sum of weights
        int totalWeight = 0;
        // Every invoker has the same weight?
        boolean sameWeight = true;
        for (int i = 0; i < length; i++) {
            // 获取每个的权重
            int weight = getWeight(invokers.get(i), invocation);

            // Sum
            totalWeight += weight;
            // 如果 sameWeight = true 比较是不是和前一个权重一样，
            if (sameWeight && i > 0
                    && weight != getWeight(invokers.get(i - 1), invocation)) {
                sameWeight = false;
            }
        }
        if (totalWeight > 0 && !sameWeight) {
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on totalWeight.
            // 如果【不是所有的invoker 都有相同的权重】 并且 【至少有一个invoker权重大于0】，根据总权重随机
            int offset = random.nextInt(totalWeight);
            //调试代码
            // Return a invoker based on the random value.
            for (int i = 0; i < length; i++) {
                // 拿随机值减去每一个值，直到 offset<0，选中invoker，权重越大概率会越高。但是也是随机的
                offset -= getWeight(invokers.get(i), invocation);
                if (offset < 0) {
                    return invokers.get(i);
                }
            }
        }
        // If all invokers have the same weight value or totalWeight=0, return evenly.
        // 如果权重都一样。直接随机选中一个
        return invokers.get(random.nextInt(length));
    }

}
