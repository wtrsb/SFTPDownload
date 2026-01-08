import paramiko
import sys
import os

def download_sftp_with_key():
    """
    ★paramikoのインストール忘れず★

    コマンドライン引数からSFTP接続情報を受け取り、鍵認証でファイルをダウンロードする。
    期待される引数 (sys.argv[1:])：
    [1] hostname, [2] port, [3] username, [4] private_key_path, [5] remote_file_path, [6] local_file_path, [7] passphrase (省略可)
    """

    # 引数のバリデーション (必須引数は6つ)
    if len(sys.argv) < 7:
        print("エラー: 必須パラメータが不足しています。")
        print("使用法: python script.py <host> <port> <user> <key_path> <remote_path> <local_path> [passphrase]")
        sys.exit(1)

    try:
        # コマンドライン引数の取得
        hostname = sys.argv[1]
        port = int(sys.argv[2])
        username = sys.argv[3]
        private_key_path = sys.argv[4]
        remote_file_path = sys.argv[5]
        local_file_path = sys.argv[6]
        # パスフレーズは省略可能 (7番目の引数として存在する場合のみ取得)
        passphrase = sys.argv[7] if len(sys.argv) > 7 else None

        # 鍵認証と接続処理
        ssh_client = paramiko.SSHClient()
        # ホストキーが未登録の場合に自動追加するポリシーを設定
        ssh_client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

        print(f"接続先: {username}@{hostname}:{port}")

        # 秘密鍵のロード
        if not os.path.exists(private_key_path):
            print(f"エラー: 秘密鍵ファイルが見つかりません: {private_key_path}")
            sys.exit(1)

        # ParamikoはRSA, DSA, ECDSA, Ed25519などに対応
        # 鍵の形式に併せて下記変更する
        private_key = paramiko.Ed25519Key.from_private_key_file(private_key_path, password=passphrase)

        # SSH接続の確立
        ssh_client.connect(
            hostname=hostname,
            port=port,
            username=username,
            pkey=private_key, # 秘密鍵オブジェクトを渡す
            timeout=10
        )
        print("SSH接続成功。")

        # SFTPクライアントの開始
        sftp_client = ssh_client.open_sftp()
        print("open_sftp")
        print("remote_file_path:" + remote_file_path)
        print("local_file_path:" + local_file_path)

        # SFTPファイルダウンロード
        # --- ファイル存在チェックの追加 ---
        try:
            sftp_client.stat(remote_file_path)
            print(f"ファイルを確認しました: {remote_file_path}")

            # 存在する場合のみダウンロード実行
            sftp_client.get(remote_file_path, local_file_path)
            print(f"ダウンロード成功: {remote_file_path} -> {local_file_path}")

        except FileNotFoundError:
            print(f"通知: リモートファイルが存在しないため、スキップします: {remote_file_path}")
        # ------------------------------

        # 閉じる
        sftp_client.close()

    except paramiko.AuthenticationException as e:
        print("認証失敗: ユーザー名、秘密鍵ファイル、またはパスフレーズが間違っています:{e}")
        sys.exit(2)
    except paramiko.SSHException as e:
        print(f"SSH接続エラー: {e}")
        sys.exit(3)
    except Exception as e:
        print(f"予期せぬエラーが発生しました: {e}")
        sys.exit(4)
    finally:
        if 'ssh_client' in locals() and ssh_client:
            ssh_client.close()

if __name__ == "__main__":
    # メイン処理
    download_sftp_with_key()

