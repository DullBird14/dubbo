package org.dullbird.demo.spi;

/**
 * @author cys
 * @date 2020-01-28 19:39
 */

public class ChineseSayServiceImpl implements HelloService {
	@Override
	public String say() {
		return "Chinese say!";
	}
}
