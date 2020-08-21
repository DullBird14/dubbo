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
package com.alibaba.dubbo.common.compiler.support;

import com.alibaba.dubbo.common.utils.ClassHelper;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavassistCompiler. (SPI, Singleton, ThreadSafe)
 */
public class JavassistCompiler extends AbstractCompiler {
    /**
     * 匹配 import
     */
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+([\\w\\.\\*]+);\n");
    /**
     * 匹配 extends
     */
    private static final Pattern EXTENDS_PATTERN = Pattern.compile("\\s+extends\\s+([\\w\\.]+)[^\\{]*\\{\n");
    /**
     * 匹配 implements
     */
    private static final Pattern IMPLEMENTS_PATTERN = Pattern.compile("\\s+implements\\s+([\\w\\.]+)\\s*\\{\n");
    /**
     * 匹配方法修饰
     */
    private static final Pattern METHODS_PATTERN = Pattern.compile("\n(private|public|protected)\\s+");
    /**
     * 匹配类的属性
     */
    private static final Pattern FIELD_PATTERN = Pattern.compile("[^\n]+=[^\n]+;");

    @Override
    public Class<?> doCompile(String name, String source) throws Throwable {
        int i = name.lastIndexOf('.');
        // 获取类名
        String className = i < 0 ? name : name.substring(i + 1);
        ClassPool pool = new ClassPool(true);
        pool.appendClassPath(new LoaderClassPath(ClassHelper.getCallerClassLoader(getClass())));
        //匹配import
        Matcher matcher = IMPORT_PATTERN.matcher(source);
        List<String> importPackages = new ArrayList<String>();
        Map<String, String> fullNames = new HashMap<String, String>();
        while (matcher.find()) {
            String pkg = matcher.group(1);
            if (pkg.endsWith(".*")) {
                // 如果import a.b.c.*
                String pkgName = pkg.substring(0, pkg.length() - 2);
                // 存入包名称 a.b.c
                pool.importPackage(pkgName);
                importPackages.add(pkgName);
            } else {
                // 如果import a.b.c.Hello
                int pi = pkg.lastIndexOf('.');
                if (pi > 0) {
                    String pkgName = pkg.substring(0, pi);
                    // 存入包名称 a.b.c
                    pool.importPackage(pkgName);
                    importPackages.add(pkgName);
                    //  存入 Hello
                    fullNames.put(pkg.substring(pi + 1), pkg);
                }
            }
        }
        String[] packages = importPackages.toArray(new String[0]);
        matcher = EXTENDS_PATTERN.matcher(source);
        CtClass cls;
        if (matcher.find()) {
            // 如果匹配到继承
            String extend = matcher.group(1).trim();
            String extendClass;
            if (extend.contains(".")) {
                // 如果继承是全类名的类，内部类
                extendClass = extend;
            } else if (fullNames.containsKey(extend)) {
                // 如果是继承导入的包下的类
                extendClass = fullNames.get(extend);
            } else {
                // 在导入的所有包中找这个类
                extendClass = ClassUtils.forName(packages, extend).getName();
            }
            // 创建 CtClass 对象
            cls = pool.makeClass(name, pool.get(extendClass));
        } else {
            cls = pool.makeClass(name);
        }
        matcher = IMPLEMENTS_PATTERN.matcher(source);
        if (matcher.find()) {
            // 如果有实现接口
            String[] ifaces = matcher.group(1).trim().split("\\,");
            for (String iface : ifaces) {
                iface = iface.trim();
                String ifaceClass;
                if (iface.contains(".")) {
                    ifaceClass = iface;
                } else if (fullNames.containsKey(iface)) {
                    ifaceClass = fullNames.get(iface);
                } else {
                    ifaceClass = ClassUtils.forName(packages, iface).getName();
                }
                cls.addInterface(pool.get(ifaceClass));
            }
        }
        // 代码正文
        String body = source.substring(source.indexOf("{") + 1, source.length() - 1);
        String[] methods = METHODS_PATTERN.split(body);
        for (String method : methods) {
            // 匹配方法
            method = method.trim();
            if (method.length() > 0) {
                if (method.startsWith(className)) {
                    // 构造方法
                    cls.addConstructor(CtNewConstructor.make("public " + method, cls));
                } else if (FIELD_PATTERN.matcher(method).matches()) {
                    // 属性
                    cls.addField(CtField.make("private " + method, cls));
                } else {
                    // 方法
                    cls.addMethod(CtNewMethod.make("public " + method, cls));
                }
            }
        }
        // 生成类
        return cls.toClass(ClassHelper.getCallerClassLoader(getClass()), JavassistCompiler.class.getProtectionDomain());
    }

}
