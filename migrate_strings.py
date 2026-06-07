import os
import shutil

src_base = r"d:\project pribadi\nekuva-kmp\nekuva\app\src\main\res"
dst_base = r"d:\project pribadi\nekuva-kmp\nekuva\composeApp\src\commonMain\composeResources"

def migrate_batch(dirs_to_migrate):
    for d in dirs_to_migrate:
        src_dir = os.path.join(src_base, d)
        
        # Handle renames
        dst_d = d
        if d == "values-in":
            dst_d = "values-id"
        elif d == "values-iw":
            dst_d = "values-he"
        elif d == "values-ji":
            dst_d = "values-yi"
            
        dst_dir = os.path.join(dst_base, dst_d)
        os.makedirs(dst_dir, exist_ok=True)
        
        files_to_copy = ["strings.xml", "plurals.xml", "arrays.xml"]
        for f in files_to_copy:
            src_file = os.path.join(src_dir, f)
            if os.path.exists(src_file):
                dst_file = os.path.join(dst_dir, f)
                shutil.copy2(src_file, dst_file)
                print(f"Copied {src_file} -> {dst_file}")
            else:
                pass # Print skipped if needed

if __name__ == "__main__":
    # Batch 3: All remaining valid language directories
    remaining = [
        "values-ab", "values-ar", "values-arq", "values-arz", "values-as", 
        "values-b+yue+Hant", "values-bci", "values-be", "values-bn", "values-ca", 
        "values-ckb", "values-cs", "values-el", "values-en-rGB", "values-enm", 
        "values-et", "values-eu", "values-fa", "values-fi", "values-fil", 
        "values-frp", "values-got", "values-gu", "values-hi", "values-hr", 
        "values-hu", "values-it", "values-ja", "values-jv", "values-kk", 
        "values-km", "values-ko", "values-lt", "values-lv", "values-lzh", 
        "values-ml", "values-ms", "values-my", "values-nb-rNO", "values-ne", 
        "values-nl", "values-nn", "values-or", "values-pa", "values-pa-rPK", 
        "values-pl", "values-pt", "values-pt-rBR", "values-ro", "values-ru", 
        "values-si", "values-sr", "values-sv", "values-ta", "values-te", 
        "values-th", "values-tr", "values-uk", "values-vi", "values-zh-rCN", 
        "values-zh-rTW"
    ]
    migrate_batch(remaining)
