//package com.alibaba.dubbo.common.bytecode;
//
///**
// * @author dullBird
// * @version 1.0.0
// * @createTime 2020年05月14日 21:16:00
// */
//public class Test {
//    public Object invokeMethod(Object o, String n, Class[] p, Object[] v)
//            throws java.lang.reflect.InvocationTargetException {
//        com.alibaba.dubbo.demo.DemoService w;
//        try {
//            w = ((com.alibaba.dubbo.demo.DemoService) $1);
//        } catch (Throwable e) {
//            throw new IllegalArgumentException(e);
//        }
//        try {
//            if ("sayHello".equals($2) && $3.length == 1) {
//                return ($w) w.sayHello((java.lang.String) $4[0]);
//            }
//        } catch (Throwable e) {
//            throw new java.lang.reflect.InvocationTargetException(e);
//        }
//        throw new com.alibaba.dubbo.common.bytecode.NoSuchMethodException("Not " +
//                "found method \"" + $2 + "\" in class com.alibaba.dubbo.demo.DemoService.");
//    }
//}
