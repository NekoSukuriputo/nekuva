import os

root_dir = 'composeApp/src/jvmSharedMain/kotlin/org/nekosukuriputo/nekuva/local'

for subdir, dirs, files in os.walk(root_dir):
    for file in files:
        if file.endswith('.kt'):
            path = os.path.join(subdir, file)
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()

            new_content = content
            new_content = new_content.replace('import android.net.Uri', 'import java.net.URI')
            new_content = new_content.replace('import androidx.core.net.toUri', '')
            new_content = new_content.replace('import androidx.core.net.toFile', '')
            
            # Types
            new_content = new_content.replace(' Uri', ' URI')
            new_content = new_content.replace('(Uri', '(URI')
            new_content = new_content.replace('<Uri>', '<URI>')
            
            # Method calls
            new_content = new_content.replace('.toUri()', '.let { URI(it) }')
            new_content = new_content.replace('.toFile()', '.let { java.io.File(it) }')

            if new_content != content:
                with open(path, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                print(f"Updated {path}")
