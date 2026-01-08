package com.operations.SFTPDownloader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * SFTPサーバーからのファイルダウンロード機能を提供するクラス。
 * <p>
 * このクラスは、Pythonスクリプトを使用してSFTPサーバーへの接続と
 * ファイル操作を行います。
 * </p>
 *
 */
public class SftpDownloader {

	protected static Logger logger = LogManager.getLogger();
    private final Properties props = new Properties();

	/**
	 * コンストラクタ。 プロパティを設定。
	 */
	public SftpDownloader() {
	}

	/**
	 * ファイルをダウンロードする
	 * @throws Exception
	 */
	public void download() throws Exception {

        try {

            if (!loadConfig("config.properties")) {
                throw new Exception("properties取得失敗");
            }

			//各種パラメータ取得
            String strSftpHost = props.getProperty("sftp.host");
            int intSftpPort = Integer.parseInt(props.getProperty("sftp.port", "22"));
            String strSftpUser = props.getProperty("sftp.username");
            String strSftpKeyFilePath = props.getProperty("sftp.KeyFilePath");
            String strSftpKeyFile = props.getProperty("sftp.KeyFile");
            String strLocalPath = props.getProperty("sftp.localPath");
			String sftpHostPath = props.getProperty("sftp.TargetFilePath");

			logger.debug("sftpHostPath:" + sftpHostPath);
            logger.info("Connecting to " + strSftpHost + ":" + intSftpPort + "...");

			// 取得対象ファイルについて設定する
            String downloadFile = props.getProperty("sftp.TargetFile");

			// get (ファイルダウンロード)
			// ログ出力
			logger.info("Downloading file: " + downloadFile);
			PythonKick(
				strSftpHost
				,intSftpPort
				,strSftpUser
				,strSftpKeyFilePath + strSftpKeyFile
				,sftpHostPath + downloadFile
				,strLocalPath + downloadFile
				,null);

			logger.debug("Download complete.");

        } catch (Exception e) {
            logger.debug("SFTP operation failed:");
            e.printStackTrace();
			throw new Exception("python SFTP接続スクリプト失敗");
        }
	}

    private boolean loadConfig(String fileName) {
        try (InputStream input = new FileInputStream(fileName)) {
            props.load(input);
            logger.error("Configuration loaded: " + fileName);
            return true;
        } catch (IOException ex) {
            logger.error("Error: Could not find " + fileName + " in the current directory.");
            return false;
        }
    }

	/**
     * Python SFTP ダウンロードスクリプトを外部プロセスとして実行する。
     *
     * @param host SFTPサーバーのホスト名
     * @param port SFTPサーバーのポート番号
     * @param user SFTPユーザー名
     * @param keyPath 秘密鍵ファイルのローカル絶対パス
     * @param remoteFile ダウンロードするリモートファイルのパス
     * @param localFile ローカルの保存先パス
     * @param passphrase 秘密鍵のパスフレーズ (nullを許容)
	 * @throws Exception 
     */
    private void PythonKick(
        String host,
        int port,
        String user,
        String keyPath,
        String remoteFile,
        String localFile,
        String passphrase
    ) throws Exception {

		//Pythonスクリプトの情報取得
		String strPythonScryptPath = props.getProperty("python.scryptPath");
		String strPythonScryptFile = props.getProperty("python.scryptFile");

       // Pythonスクリプトへの引数リストを作成
        List<String> command = new ArrayList<>();
        command.add("python"); // 1. Python実行コマンド
        command.add(strPythonScryptFile); // 2. スクリプトファイル名
        command.add(host); // 3. hostname
        command.add(String.valueOf(port)); // 4. port
        command.add(user); // 5. username
        command.add(keyPath); // 6. private_key_path
        command.add(remoteFile); // 7. remote_file_path
        command.add(localFile); // 8. local_file_path

        // パスフレーズは null でない場合のみ追加
        if (passphrase != null) {
            command.add(passphrase); // 9. passphrase (任意)
        }
		logger.debug("command: " + command);

		// --- 実行処理 ---
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
			// 環境変数 PYTHONIOENCODING を設定し、出力ストリームに UTF-8 を強制
			pb.environment().put("PYTHONIOENCODING", "utf-8");

			//※今後必要あれば
			//ちなみにない場合、jarと同フォルダが実行対象
			if(!strPythonScryptPath.isEmpty()){
				// スクリプトの格納ディレクトリを作業ディレクトリに設定　
				pb.directory(new File(strPythonScryptPath));
			}

            Process p = pb.start();

            // Pythonの標準出力 (成功メッセージ) を読み取る
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("INFO: " + line);
                }
            }

            // Pythonの標準エラー出力 (エラーメッセージ) を読み取る
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    logger.error("ERROR: " + line);
                }
            }

            int exitCode = p.waitFor(); // Pythonスクリプトの終了を待機

            if (exitCode == 0) {
                logger.debug("SFTPダウンロード処理が正常に完了しました。");
            } else {
                logger.debug("SFTPダウンロード処理がエラー終了しました (終了コード: " + exitCode + ")");
				throw new Exception("SFTPダウンロード処理がエラー");
            }

        } catch (Exception e) {
			logger.debug("例外：" + e);
            e.printStackTrace();
			throw new Exception("python SFTP接続スクリプト失敗");
        }
    }
}
