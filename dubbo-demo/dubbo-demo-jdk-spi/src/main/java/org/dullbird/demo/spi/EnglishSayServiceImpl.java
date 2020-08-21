package org.dullbird.demo.spi;

/**
 * @author cys
 * @date 2020-01-28 19:38
 */

public class EnglishSayServiceImpl implements HelloService {
	@Override
	public String say() {
		return "English say!";
	}
}
