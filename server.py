import socket
import subprocess
import os

HOST = '0.0.0.0'
PORT = 7777

# Начальная директория
current_dir = os.path.expanduser("~")

def get_local_ip():
    """Функция для автоматического определения IP-адреса компьютера в локальной сети"""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # Не обязательно, чтобы этот адрес был доступен, сокет просто выберет нужный интерфейс
        s.connect(('8.8.8.8', 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

def execute_command(cmd):
    global current_dir
    
    # Логика смены директории
    if cmd.lower().startswith("cd "):
        new_path = cmd[3:].strip().strip('"')
        potential_path = os.path.abspath(os.path.join(current_dir, new_path))
        if os.path.exists(potential_path) and os.path.isdir(potential_path):
            current_dir = potential_path
            return f"Переход в: {current_dir}"
        else:
            return f"Ошибка: папка не найдена: {new_path}"

    # Команда для файлового менеджера (на будущее)
    if cmd == "__list__":
        try:
            items = os.listdir(current_dir)
            res = []
            for item in items:
                p = "DIR" if os.path.isdir(os.path.join(current_dir, item)) else "FILE"
                res.append(f"{p}|{item}")
            return "\n".join(res)
        except Exception as e:
            return str(e)

    # Обычные системные команды
    try:
        # /c - выполнить и закрыть, /d - менять диск при cd
        full_cmd = f'cd /d "{current_dir}" && {cmd}'
        result = subprocess.run(
            full_cmd, shell=True, capture_output=True, text=True, encoding='cp866', errors='replace'
        )
        return result.stdout + result.stderr
    except Exception as e:
        return f"Ошибка сервера: {str(e)}"

def start_server():
    my_ip = get_local_ip()
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    try:
        server.bind((HOST, PORT))
    except Exception as e:
        print(f"[!] Не удалось запустить сервер: {e}")
        return

    server.listen(1)
    
    print("="*40)
    print(f"СТАТУС: Сервер запущен!")
    print(f"IP АДРЕС: {my_ip}")
    print(f"ПОРТ: {PORT}")
    print(f"ПАПКА: {current_dir}")
    print("="*40)
    print("Ожидание подключения телефона...")

    while True:
        conn, addr = server.accept()
        print(f"[+] Подключен телефон: {addr[0]}")
        try:
            while True:
                data = conn.recv(16384)
                if not data: break
                
                command = data.decode('utf-8').strip()
                if not command: # Пустая команда (например, при коннекте)
                    response = ""
                elif command.lower() == 'exit':
                    break
                else:
                    print(f"[CMD]: {command}")
                    response = execute_command(command)
                
                # Формируем ответ с путем и маркером конца
                full_response = f"{response}\n\n[PATH]{current_dir}\nEND_OF_OUTPUT\n"
                conn.sendall(full_response.encode('utf-8', errors='ignore'))
                
        except Exception as e:
            print(f"[!] Разрыв связи: {e}")
        finally:
            conn.close()
            print(f"[-] Телефон отключен")

if __name__ == "__main__":
    start_server()