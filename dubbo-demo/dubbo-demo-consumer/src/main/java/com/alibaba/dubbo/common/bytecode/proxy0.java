//package com.alibaba.dubbo.common.bytecode;
//
//import com.alibaba.dubbo.common.bytecode.ClassGenerator;
//import com.alibaba.dubbo.demo.DemoService;
//import com.alibaba.dubbo.rpc.service.EchoService;
//import java.lang.reflect.InvocationHandler;
//import java.lang.reflect.Method;
//
//public class proxy0
//		implements ClassGenerator.DC,
//		EchoService,
//		DemoService {
//	public static Method[] methods;
//	private InvocationHandler handler;
//
//	@Override
//	public String sayHello(String string) {
//		Object[] arrobject = new Object[]{string};
//		Object object = this.handler.invoke(this, methods[0], arrobject);
//		return (String)object;
//	}
//
//	public Object $echo(Object object) {
//		Object[] arrobject = new Object[]{object};
//		Object object2 = this.handler.invoke(this, methods[1], arrobject);
//		return object2;
//	}
//
//	public proxy0() {
//	}
//
//	public proxy0(InvocationHandler invocationHandler) {
//		this.handler = invocationHandler;
//	}
//}
