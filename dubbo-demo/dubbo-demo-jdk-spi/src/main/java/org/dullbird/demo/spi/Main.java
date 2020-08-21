package org.dullbird.demo.spi;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * @author cys
 * @date 2020-01-28 19:37
 */

public class Main {
	public static void main(String[] args) {
		ServiceLoader<HelloService> helloServices = ServiceLoader.load(HelloService.class);
		Iterator<HelloService> iterator = helloServices.iterator();
		while (iterator != null
				&& iterator.hasNext()) {
			HelloService next = iterator.next();
			System.out.println(next.say());
		}
	}
}
