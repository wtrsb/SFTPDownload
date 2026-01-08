package com.operations.SFTPDownloader;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.Factory;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.kex.KeyExchange;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.xfer.FileSystemFile;

/**
 * SFTPサーバーからのファイルダウンロード機能を提供するクラス。
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
			//SFTPサーバ上のパスを生成
			String sftpHostPath = props.getProperty("sftp.TargetFilePath");

            logger.debug("sftpHostPath:" + sftpHostPath);
            logger.info("Connecting to " + strSftpHost + ":" + intSftpPort + "...");

			// 取得対象ファイルについて設定する
            String downloadFile = props.getProperty("sftp.TargetFile");

			// get (ファイルダウンロード)
			// ログ出力
			logger.info("Downloading file: " + downloadFile);
			sftpGet(
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
     * SFTP ダウンロードプロセス実行
     * @param host SFTPサーバーのホスト名
     * @param port SFTPサーバーのポート番号
     * @param user SFTPユーザー名
     * @param keyPath 秘密鍵ファイルのローカル絶対パス
     * @param remoteFile ダウンロードするリモートファイルのパス
     * @param localFile ローカルの保存先パス
     * @param passphrase 秘密鍵のパスフレーズ (nullを許容)
	 * @throws Exception 
     */
    private void sftpGet(
        String host,
        int port,
        String user,
        String keyPath,
        String remoteFile,
        String localFile,
        String passphrase
    ) throws Exception {

    	// セキュリティプロバイダを「最優先(1)」で登録
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
        }

    	// 2. DefaultConfigは引数なしで生成（自動的にBCが使われる）
        DefaultConfig config = new DefaultConfig();
    	// X25519をリストから外すことで、エラーを回避します
    	// 現在の全ファクトリから "curve25519" を含むものだけを除外する
    	// 現在のリストを取得し、変更可能な新しいリストにコピーする
        List<Factory.Named<KeyExchange>> kexFactories = new ArrayList<>(config.getKeyExchangeFactories());

    	// 問題のアルゴリズムを除外する
        kexFactories.removeIf(factory -> factory.getName().contains("curve25519"));

    	// 修正したリストをConfigに再セットする
        config.setKeyExchangeFactories(kexFactories);
        logger.debug("First Provider: " + Security.getProviders()[0].getName());

        // --- 実行処理 ---
        try (SSHClient ssh = new SSHClient(config)) {
            // ホストキーの検証（デモ用に全て許可する設定）
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            // 接続
            ssh.connect(host);
            // 秘密鍵の読み込み
            // パスフレーズがある場合は loadKeys(path, passphrase) を使用
            KeyProvider keys = ssh.loadKeys(keyPath);

            // 公開鍵認証の実行
            ssh.authPublickey(user, keys);

            // SFTPセッションの開始
            try (SFTPClient sftp = ssh.newSFTPClient()) {

                // get(リモートパス, ローカルパス)
                // FileSystemFile クラスを使用してローカルの保存先を指定します
                sftp.get(remoteFile, new FileSystemFile(localFile));

            }
        } catch (Exception e) {
			logger.debug("例外：" + e);
            e.printStackTrace();
			throw new Exception("SFTP接続失敗");
        }
    }
}
