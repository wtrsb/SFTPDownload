package com.operations.SFTPDownloader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * 呼び出し
 */
public class Application {

	public static void main(String[] args) {

		Logger logger = LogManager.getLogger();
		try {
			logger.info("処理　開始");

			new SftpDownloader().download();

			logger.info("処理　成功");

		} catch (Exception e) {
			logger.error("処理　失敗", e);
		}
	}

}
