import socket
import threading
import os
import subprocess

HOST = '0.0.0.0'
PORT = 7777
DISCOVERY_PORT = 7778

# Начальная директория
current_dir = os.path.expanduser("~")

def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

def discovery_worker():
    """Отвечает на UDP-запросы телефона для автопоиска"""
    udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        udp.bind(('', DISCOVERY_PORT))
    except Exception as e:
        print(f"[!] Ошибка UDP: {e}")
        return

    while True:
        try:
            data, addr = udp.recvfrom(1024)
            if data == b"WHERE_IS_REMOTE_CMD?":
                # Отправляем подтверждение, что мы — сервер
                udp.sendto(b"I_AM_REMOTE_CMD_SERVER", addr)
        except:
            continue

def execute_command(cmd):
    global current_dir
    if cmd.lower().startswith("cd "):
        new_path = cmd[3:].strip().strip('"')
        potential_path = os.path.abspath(os.path.join(current_dir, new_path))
        if os.path.exists(potential_path) and os.path.isdir(potential_path):
            current_dir = potential_path
            return f"Переход в: {current_dir}"
        else:
            return f"Ошибка: папка не найдена: {new_path}"

    try:
        full_cmd = f'cd /d "{current_dir}" && {cmd}'
        result = subprocess.run(
            full_cmd, shell=True, capture_output=True, text=True, encoding='cp866', errors='replace'
        )
        return result.stdout + result.stderr
    except Exception as e:
        return f"Ошибка сервера: {str(e)}"

def start_server():
    # Запускаем автопоиск в отдельном потоке
    threading.Thread(target=discovery_worker, daemon=True).start()

    my_ip = get_local_ip()
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen(1)
    
    print(f"[*] Сервер запущен на {my_ip}:{PORT}")
    print("[*] Автопоиск активен на порту 7778")

    while True:
        conn, addr = server.accept()
        try:
            while True:
                data = conn.recv(16384)
                if not data: break
                command = data.decode('utf-8').strip()
                if not command: response = ""
                elif command.lower() == 'exit': break
                else: response = execute_command(command)
                
                full_response = f"{response}\n\n[PATH]{current_dir}\nEND_OF_OUTPUT\n"
                conn.sendall(full_response.encode('utf-8', errors='ignore'))
        except:
            pass
        finally:
            conn.close()

if __name__ == "__main__":
    start_server()