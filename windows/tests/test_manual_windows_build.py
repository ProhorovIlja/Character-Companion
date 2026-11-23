"""Windows packaging and bounded child-output source audit, not Windows execution."""
from pathlib import Path
import re
import unittest
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]

class ManualWindowsBuild(unittest.TestCase):
    def test_manual_publish_checks_sdk_and_runs_real_smoke(self):
        script = (ROOT / 'windows/build-windows.ps1').read_text(encoding='utf-8')
        self.assertIn("dotnet --list-sdks", script)
        self.assertIn("dotnet run --project $smoke", script)
        self.assertIn("dotnet publish $project", script)
        self.assertIn("-p:PublishSingleFile=false", script)
        self.assertIn("-p:PublishTrimmed=false", script)
        self.assertIn("-p:PublishReadyToRun=false", script)
        self.assertIn("Test-Path -LiteralPath $exe", script)
        self.assertIn("Test-Path -LiteralPath $cef", script)
        self.assertIn("Test-Path -LiteralPath (Join-Path $published 'locales')", script)
        self.assertIn("Get-FileHash -LiteralPath $exe -Algorithm SHA256", script)
        self.assertNotIn('Start-Job',script)
        self.assertNotIn('Register-ScheduledTask',script)
        self.assertNotIn('New-ScheduledTask',script)
        self.assertNotIn('gh release create',script)

    def test_production_whisper_output_consumption_is_bounded_memory(self):
        source=(ROOT/'windows/CharacterCompanion.Windows/Speech/WhisperCliTranscriber.cs').read_text(encoding='utf-8')
        self.assertNotIn('ReadToEndAsync(', source)
        self.assertIn('new char[4 * 1024]', source)
        self.assertIn('reader.ReadAsync(buffer.AsMemory(), cancellationToken)', source)
        self.assertIn('DrainOutputAsync(process.StandardOutput, timeout.Token)',source)
        self.assertIn('DrainOutputAsync(process.StandardError, timeout.Token)',source)
        self.assertIn('process.WaitForExitAsync(timeout.Token)',source)
        self.assertIn('process.Kill(entireProcessTree: true)',source)
        self.assertIn('await process.WaitForExitAsync(CancellationToken.None)',source)

    def test_prod_whisper_is_compiled_by_future_dotnet_policy_smoke(self):
        project=ElementTree.parse(ROOT/'windows/PolicySmoke/CharacterCompanion.PolicySmoke.csproj').getroot()
        linked=[n.attrib['Include'] for n in project.findall('.//Compile')]
        self.assertIn('../CharacterCompanion.Windows/Speech/WhisperCliTranscriber.cs',linked)
        program=(ROOT/'windows/PolicySmoke/Program.cs').read_text(encoding='utf-8')
        self.assertIn('WhisperCliTranscriber.TranscribeAsync',program)

if __name__=='__main__': unittest.main()
