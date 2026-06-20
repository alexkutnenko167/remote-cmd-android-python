import socket
import threading
import os
import subprocess

HOST = '0.0.0.0'
PORT = 7777
DISCOVERY_PORT = 7778

# Устанавливаем начальную директорию правильно
current_dir = os.path.abspath(os.path.expanduser("~"))
os.chdir(current_dir)

def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 1))
        ip = s.getsockname()[0]
    except:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

def discovery_worker():
    udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        udp.bind(('', DISCOVERY_PORT))
    except Exception as e:
        print(f"UDP Error: {e}")
        return
    while True:
        try:
            data, addr = udp.recvfrom(1024)
            if data == b"WHERE_IS_REMOTE_CMD?":
                udp.sendto(b"I_AM_REMOTE_CMD_SERVER", addr)
        except: continue

def execute_command(cmd):
    global current_dir
    cmd = cmd.strip()
    if not cmd: return ""

    # 1. НАВИГАЦИЯ (CD)
    if cmd.lower().startswith("cd "):
        target = cmd[3:].strip().strip('"')
        try:
            # Строим путь и нормализуем его (убираем лишние .. и слеши)
            new_path = os.path.normpath(os.path.join(current_dir, target))
            if os.path.isdir(new_path):
                current_dir = new_path
                os.chdir(current_dir) # Физически переходим
                return f"PATH_CHANGED|{current_dir}"
            else:
                return "ERROR|Директория не найдена"
        except Exception as e:
            return f"ERROR|{str(e)}"

    # 2. ПРОВОДНИК (__list__)
    if cmd == "__list__":
        try:
            # Принудительно проверяем текущую папку
            os.chdir(current_dir)
            items = os.listdir(current_dir)
            # Сортировка: папки сверху
            items.sort(key=lambda x: (not os.path.isdir(os.path.join(current_dir, x)), x.lower()))
            
            res = []
            for i in items:
                prefix = "DIR" if os.path.isdir(os.path.join(current_dir, i)) else "FILE"
                res.append(f"{prefix}|{i}")
            return "\n".join(res) if res else "EMPTY_DIR"
        except Exception as e:
            return f"ERROR|Ошибка доступа: {str(e)}"

    # 3. ОТКРЫТИЕ (Музыка, Видео, Доки)
    if cmd.startswith("__open__ "):
        filename = cmd[9:].strip('"')
        filepath = os.path.normpath(os.path.join(current_dir, filename))
        try:
            # Используем shell=True через start для максимальной совместимости с музыкой
            os.startfile(filepath)
            return f"SUCCESS|Запущен файл: {filename}"
        except Exception as e:
            return f"ERROR|Не удалось открыть: {str(e)}"

    # 4. ОБЫЧНЫЕ КОМАНДЫ
    try:
        # cd /d гарантирует смену диска (например с C на D)
        result = subprocess.run(
            f'cd /d "{current_dir}" && {cmd}',
            shell=True, capture_output=True, text=True, encoding='cp866', errors='replace'
        )
        return (result.stdout + result.stderr).strip()
    except Exception as e:
        return f"ERROR|{str(e)}"

def start_server():
    threading.Thread(target=discovery_worker, daemon=True).start()
    my_ip = get_local_ip()
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen(1)
    print(f"[*] Сервер на {my_ip}, жду телефон...")

    while True:
        conn, addr = server.accept()
        try:
            while True:
                data = conn.recv(16384)
                if not data: break
                response = execute_command(data.decode('utf-8'))
                full_response = f"{response}\n\n[PATH]{current_dir}\nEND_OF_OUTPUT\n"
                conn.sendall(full_response.encode('utf-8', errors='ignore'))
        except: pass
        finally: conn.close()

if __name__ == "__main__":
    start_server()