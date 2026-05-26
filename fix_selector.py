import json

with open('/Users/xera/.gemini/antigravity/brain/899ad40f-0683-46f9-862f-72e270b9cdac/.system_generated/logs/transcript.jsonl', 'r', encoding='utf-8') as f:
    for line in f:
        if 'NodeCache' in line and '"TargetFile"' in line:
            try:
                data = json.loads(line)
                for tc in data.get('tool_calls', []):
                    args = tc.get('args', {})
                    if tc.get('name') == 'replace_file_content':
                        replacement = args.get('ReplacementContent')
                        if replacement and 'class NodeCache' in replacement:
                            # It's a full replacement, or at least contains NodeCache
                            with open('/Users/xera/GitHub/x-clicker/app/src/main/java/dev/xera/xclicker/service/selector/AndroidNodeTransform.kt', 'w', encoding='utf-8') as out_f:
                                out_f.write(replacement)
                            print("Wrote AndroidNodeTransform.kt!")
                            exit(0)
            except Exception:
                pass
