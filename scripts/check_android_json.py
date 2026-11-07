"""Catch org.json calls that desktop tests support but Android's framework does not."""
import os, pathlib, re, subprocess, sys

root=pathlib.Path(__file__).resolve().parents[1]
sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:
    props=root/'local.properties'
    if props.exists():
        sdk=next((line.split('=',1)[1] for line in props.read_text().splitlines() if line.startswith('sdk.dir=')),None)
if not sdk:
    sys.exit('ANDROID_HOME is required for the Android JSON compatibility check')
jar=pathlib.Path(sdk)/'platforms/android-35/android.jar'
javap=str(pathlib.Path(os.environ['JAVA_HOME'])/'bin/javap') if os.environ.get('JAVA_HOME') else 'javap'
allowed={}
for name in ['JSONObject','JSONArray','JSONTokener','JSONException']:
    output=subprocess.check_output([javap,'-p','-s','-classpath',str(jar),'org.json.'+name],text=True)
    previous=''
    for line in output.splitlines():
        if 'descriptor:' in line and '(' in previous:
            method=previous.split('(')[0].split()[-1].split('.')[-1]
            if method==name: method='<init>'
            allowed.setdefault(name,set()).add((method,line.split('descriptor:')[1].strip()))
        previous=line.strip()
errors=[]
classes=root/'core/build/classes/java/main'
for file in classes.rglob('*.class'):
    output=subprocess.check_output([javap,'-c','-p',str(file)],text=True)
    for name,method,descriptor in re.findall(r'// Method org/json/(\w+)\.([^:]+):(\S+)',output):
        if name in allowed and (method.strip('"'),descriptor) not in allowed[name]:
            errors.append(f'{file.relative_to(classes)} invokes unsupported Android {name}.{method}{descriptor}')
if errors:
    sys.exit('\n'.join(errors))
print('Android framework org.json API compatibility passed')
