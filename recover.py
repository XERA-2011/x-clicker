import json
import os
import ast

with open('/Users/xera/.gemini/antigravity/brain/899ad40f-0683-46f9-862f-72e270b9cdac/.system_generated/logs/transcript.jsonl', 'r', encoding='utf-8') as f:
    lines = f.readlines()

file_paths = [
    'app/src/main/java/dev/xera/xclicker/service/XClickerService.kt',
    'app/src/main/java/dev/xera/xclicker/service/selector/AndroidNodeTransform.kt'
]

os.system('git checkout ' + ' '.join(file_paths))

for file_path in file_paths:
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
        
    for line in lines:
        try:
            data = json.loads(line)
        except Exception as e:
            continue
            
        if data.get('type') == 'PLANNER_RESPONSE':
            tool_calls = data.get('tool_calls', [])
            for tc in tool_calls:
                if tc.get('name') == 'replace_file_content':
                    args = tc.get('args', {})
                    if file_path in args.get('TargetFile', ''):
                        target = args.get('TargetContent', '')
                        replacement = args.get('ReplacementContent', '')
                        if target in content:
                            content = content.replace(target, replacement, 1)
                elif tc.get('name') == 'multi_replace_file_content':
                    args = tc.get('args', {})
                    if file_path in args.get('TargetFile', ''):
                        chunks = args.get('ReplacementChunks', [])
                        if isinstance(chunks, str):
                            try:
                                chunks = ast.literal_eval(chunks.replace('true', 'True').replace('false', 'False'))
                            except:
                                try:
                                    chunks = json.loads(chunks, strict=False)
                                except:
                                    continue
                        if isinstance(chunks, list):
                            for chunk in chunks:
                                if isinstance(chunk, dict):
                                    target = chunk.get('TargetContent', '')
                                    replacement = chunk.get('ReplacementContent', '')
                                    if target in content:
                                        content = content.replace(target, replacement, 1)
                                
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
        
print("Recovery completed.")
