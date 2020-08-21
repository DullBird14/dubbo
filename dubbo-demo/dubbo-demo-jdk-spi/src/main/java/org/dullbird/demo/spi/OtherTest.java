package org.dullbird.demo.spi;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

/**
 * @author cys
 * @date 2020-01-29 11:12
 */

public class OtherTest {
	public static void main(String[] args) throws IOException {
		System.out.println(HelloService.class.getName());
		ClassLoader loader = OtherTest.class.getClassLoader();

//		Enumeration<URL> resources = loader.getResources("META-INF/services/" + HelloService.class.getName());
		Enumeration<URL> resources = ClassLoader.getSystemResources("META-INF/services/" + HelloService.class.getName());
		while (resources != null
				&& resources.hasMoreElements()) {
			URL url = resources.nextElement();
			System.out.println(url);
		}
	}
}
