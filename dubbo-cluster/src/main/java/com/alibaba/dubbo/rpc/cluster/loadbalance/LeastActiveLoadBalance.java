/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
		//www.apache.org/licenses/LICENSE-2.0
 *     http:
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
import com.alibaba.dubbo.rpc.RpcStatus;

import java.util.List;
import java.util.Random;

/**
 * LeastActiveLoadBalance
 * 最少链接数负载均衡
 */
public class LeastActiveLoadBalance extends AbstractLoadBalance {

	public static final String NAME = "leastactive";

	private final Random random = new Random();

	@Override
	protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
		// Number of invokers
		int length = invokers.size();
		// The least active value of all invokers
		int leastActive = -1;
		// The number of invokers having the same least active value (leastActive)
		// 相同最小活跃数的个数
		int leastCount = 0;
		// The index of invokers having the same least active value (leastActive)
		// 相同最小活跃数的下标
		int[] leastIndexs = new int[length];
		// The sum of with warmup weights
		// 总权重
		int totalWeight = 0;
		// Initial value, used for comparision
		// 第一个权重，用于比较
		int firstWeight = 0;
		// Every invoker has the same weight value?
		boolean sameWeight = true;
		for (int i = 0; i < length; i++) {
			Invoker<T> invoker = invokers.get(i);
			// Active number
			// 活跃数
			int active = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName()).getActive();
			// Weight
			// 权重
			int afterWarmup = getWeight(invoker, invocation);
			// Restart, when find a invoker having smaller least active value.
			if (leastActive == -1 || active < leastActive) {
				// 当找到一个更小的 active 值，需要重新设置 leastActive,leastCountleastIndexs，totalWeight，firstWeight，sameWeight
				// Record the current least active value
				leastActive = active;
				// Reset leastCount, count again based on current leastCount
				// 重新设置值
				leastCount = 1;
				leastIndexs[0] = i;
				totalWeight = afterWarmup;
				// Record the weight the first invoker
				// 记录第一个
				firstWeight = afterWarmup;
				// Reset, every invoker has the same weight value?
				sameWeight = true;
				// If current invoker's active value equals with leaseActive, then accumulating.
			} else if (active == leastActive) {
				// 如果和最小值一样
				// Record index number of this invoker
				leastIndexs[leastCount++] = i;
				// Add this invoker's weight to totalWeight.
				totalWeight += afterWarmup;
				// If every invoker has the same weight?
				if (sameWeight && i > 0
						&& afterWarmup != firstWeight) {
					// 是否全部都一样的检查
					sameWeight = false;
				}
			}
		}
		// assert(leastCount > 0)
		if (leastCount == 1) {
			// 只有一个最小值
			// If we got exactly one invoker having the least active value, return this invoker directly.
			return invokers.get(leastIndexs[0]);
		}
		if (!sameWeight && totalWeight > 0) {
			// 如果都不是全部都一样
			// If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on totalWeight.
			//todo 这里不需要 +1吧？前闭后开区间，权重区间计算。和random类似
			int offsetWeight = random.nextInt(totalWeight) + 1;
			// Return a invoker based on the random value.
			for (int i = 0; i < leastCount; i++) {
				int leastIndex = leastIndexs[i];
				offsetWeight -= getWeight(invokers.get(leastIndex), invocation);
				if (offsetWeight <= 0)
					return invokers.get(leastIndex);
			}
		}
		// If all invokers have the same weight value or totalWeight=0, return evenly.
		return invokers.get(leastIndexs[random.nextInt(leastCount)]);
	}
}
