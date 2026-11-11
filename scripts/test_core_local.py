"""Run core tests with an existing JDK runtime and dependency jar directory."""
import pathlib, subprocess, sys
root=pathlib.Path(__file__).resolve().parents[1]
deps=pathlib.Path(sys.argv[1]);java=sys.argv[2] if len(sys.argv)>2 else 'java'
classes=root/'.superpowers/route-planner/classes';classes.mkdir(parents=True,exist_ok=True)
jars=':'.join(str(p) for p in deps.glob('*.jar'))
sources=list((root/'core/src/main/java').rglob('*.java'))+list((root/'core/src/test/java').rglob('*.java'))
subprocess.run([java,'com.sun.tools.javac.Main','-source','17','-target','17','-cp',jars,'-d',str(classes),*[str(p) for p in sources]],check=True)
tests=['au.com.endurancecoach.core.'+p.stem for p in (root/'core/src/test/java').rglob('*Test.java')]
subprocess.run([java,'-cp',str(classes)+':'+jars,'org.junit.runner.JUnitCore',*tests],check=True)
