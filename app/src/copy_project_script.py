import os
import sys
from pathlib import Path

def collect_kotlin_and_xml_files(root_dir: Path, output_file: Path):
    """
    Рекурсивно обходит директорию root_dir и собирает содержимое всех файлов
    с расширениями .kt и .xml в формате:
    
    путь/относительно/root_dir/файл.расширение:
    ```
    содержимое файла
    ```
    """
    with open(output_file, 'w', encoding='utf-8') as out_f:
        for file_path in sorted(root_dir.rglob('*')):
            if file_path.is_file() and file_path.suffix in ('.kt', '.xml'):
                # Получаем относительный путь от root_dir
                rel_path = file_path.relative_to(root_dir)
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        content = f.read()
                except Exception as e:
                    content = f"[Ошибка при чтении файла: {e}]"

                # Записываем в файл
                out_f.write(f"{rel_path}:\n")
                out_f.write("```\n")
                out_f.write(content)
                out_f.write("\n```\n\n")

def main():
    # Определяем директорию запуска скрипта
    script_dir = Path(__file__).parent.resolve()
    
    # Ищем папку вида AppName\app\src\main
    # Предполагаем, что скрипт запускается из корня проекта или из подпапки
    # Попробуем найти папку 'app/src/main'
    target_dir = None

    # Вариант 1: ищем app/src/main от текущей директории
    candidate = Path.cwd() / "app" / "src" / "main"
    if candidate.exists() and candidate.is_dir():
        target_dir = candidate
        # Имя приложения — имя родительской папки от app/
        app_name = (candidate.parent.parent).name
    else:
        # Вариант 2: ищем рекурсивно вверх по дереву директорий
        current = Path.cwd()
        while current != current.parent:
            candidate = current / "app" / "src" / "main"
            if candidate.exists() and candidate.is_dir():
                target_dir = candidate
                app_name = current.name
                break
            current = current.parent
        else:
            # Если не найдено, используем текущую директорию как корень
            target_dir = Path.cwd()
            app_name = target_dir.name

    if not target_dir:
        print("Не удалось определить директорию app/src/main. Используется текущая папка.")
        target_dir = Path.cwd()
        app_name = target_dir.name

    output_file = script_dir / f"{app_name}.txt"

    print(f"Сканирование директории: {target_dir}")
    print(f"Результат будет сохранён в: {output_file}")

    collect_kotlin_and_xml_files(target_dir, output_file)
    print("Готово!")

if __name__ == "__main__":
    main()