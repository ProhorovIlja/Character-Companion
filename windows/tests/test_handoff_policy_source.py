"""Source and manual-test-project checks, NOT an actual .NET compilation."""
from pathlib import Path
import unittest
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
WINDOWS = ROOT / 'windows'

class HandoffSmokeStructure(unittest.TestCase):
    def test_production_code_used_by_offline_smoke(self):
        project = ET.parse(WINDOWS/'PolicySmoke/CharacterCompanion.PolicySmoke.csproj').getroot()
        self.assertEqual(project.findtext('./PropertyGroup/TargetFramework'), 'net10.0')
        compile_items=[v.attrib['Include'] for v in project.findall('./ItemGroup/Compile')]
        self.assertEqual(compile_items, ['../CharacterCompanion.Windows/Security/SitePolicy.cs',
                                        '../CharacterCompanion.Windows/Speech/Pcm16Resampler.cs',
                                        '../CharacterCompanion.Windows/Speech/WhisperCliTranscriber.cs'])
        self.assertEqual(project.findall('.//PackageReference'), [])

    def test_coverage_of_auth_redirect_and_nested_encoding(self):
        source=(WINDOWS/'PolicySmoke/Program.cs').read_text(encoding='utf-8')
        policy=(WINDOWS/'CharacterCompanion.Windows/Security/SitePolicy.cs').read_text(encoding='utf-8')
        for marker in ('%63ode', '%2563ode', '%25252Fauth', 'access_token', 'auth0.com', 'id.apple.com',
                       'character.ai.evil.org', 'user:password@', 'statefulness'):
            self.assertIn(marker, source)
        for marker in ('SensitiveKeys', 'SensitivePathParts', 'DecodeLimited', 'SafeBrowserHandoff',
                       'UriFormat.UriEscaped', 'RegexOptions.CultureInvariant'):
            self.assertIn(marker, policy)

    def test_final_process_start_revalidates_url(self):
        source=(WINDOWS/'CharacterCompanion.Windows/MainWindow.xaml.cs').read_text(encoding='utf-8')
        self.assertIn('var safe = SitePolicy.SafeBrowserHandoff(address)', source)
        self.assertIn('new ProcessStartInfo(safe)', source)
        self.assertNotIn('new ProcessStartInfo(address)', source)

if __name__=='__main__': unittest.main()
