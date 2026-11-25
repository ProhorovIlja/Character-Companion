using System.Speech.Synthesis;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Automation;
using System.Windows.Threading;
using CharacterCompanion.Windows.Intelligence;

namespace Mandy.NativePreview;

/// <summary>Explicit, foreground playback through an installed Windows speech engine.</summary>
internal static class WindowsReadAloud
{
    private sealed record VoiceOption(string Name, string Language)
    {
        public string Label => Name + " (" + Language + ")";
    }

    public static string Show(Window owner, string text)
    {
        if (string.IsNullOrWhiteSpace(text) || text.Length > 16000)
            return "Reply cannot be read aloud.";
        try
        {
            LocalHistory.ValidateText(text);
            using var synth = new SpeechSynthesizer();
            var voices = synth.GetInstalledVoices()
                .Where(v => v.Enabled && v.VoiceInfo.Culture.TwoLetterISOLanguageName is "en" or "ru")
                .Select(v => new VoiceOption(v.VoiceInfo.Name, v.VoiceInfo.Culture.Name))
                .Distinct().OrderBy(v => v.Language, StringComparer.Ordinal)
                .ThenBy(v => v.Name, StringComparer.Ordinal).ToArray();
            if (voices.Length == 0) return "No enabled English or Russian Windows speech voice found.";

            var panel = new StackPanel { Margin = new Thickness(16) };
            panel.Children.Add(new TextBlock
            {
                Text = "Choose a trusted installed voice. The selected Windows speech engine receives the reply. Mandy does not verify third-party engine network behavior.",
                TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 0, 0, 12)
            });
            var voice = new ComboBox { ItemsSource = voices, DisplayMemberPath = nameof(VoiceOption.Label) };
            AutomationProperties.SetName(voice, "Speech voice");
            panel.Children.Add(voice);
            var state = new TextBlock { Text = "Select a voice to begin.", TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 12, 0, 8) };
            panel.Children.Add(state);
            var play = new Button { Content = "Read aloud", IsEnabled = false, IsDefault = true };
            var stop = new Button { Content = "Stop / close", IsCancel = true, Margin = new Thickness(8, 0, 0, 0) };
            var actions = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
            actions.Children.Add(play); actions.Children.Add(stop); panel.Children.Add(actions);
            var dialog = new Window
            {
                Owner = owner, Title = "Read reply aloud", Width = 420, SizeToContent = SizeToContent.Height,
                WindowStartupLocation = WindowStartupLocation.CenterOwner, ShowInTaskbar = false, Content = panel
            };
            var result = "Read aloud closed.";
            Prompt? pending = null;
            bool closed = false;
            dialog.Closed += (_, _) => closed = true;
            voice.SelectionChanged += (_, _) => play.IsEnabled = pending == null && voice.SelectedItem is VoiceOption;
            void Completed(object? sender, SpeakCompletedEventArgs e)
            {
                // Completion can arrive on a speech worker; never touch controls there.
                if (dialog.Dispatcher.HasShutdownStarted) return;
                dialog.Dispatcher.BeginInvoke(DispatcherPriority.Normal, new Action(() =>
                {
                    if (closed || !dialog.IsVisible || pending == null || !ReferenceEquals(e.Prompt, pending)) return;
                    pending = null;
                    result = e.Cancelled ? "Read aloud stopped." :
                        e.Error != null ? "Speech playback failed." : "Read aloud completed.";
                    state.Text = result;
                    voice.IsEnabled = true;
                    play.IsEnabled = voice.SelectedItem is VoiceOption;
                }));
            }
            synth.SpeakCompleted += Completed;
            play.Click += (_, _) =>
            {
                if (closed || pending != null || voice.SelectedItem is not VoiceOption selected) return;
                try
                {
                    synth.SelectVoice(selected.Name);
                    if (synth.Voice.Name != selected.Name ||
                        !string.Equals(synth.Voice.Culture.Name, selected.Language, StringComparison.Ordinal))
                        throw new InvalidOperationException("Selected voice unavailable.");
                    synth.SetOutputToDefaultAudioDevice();
                    voice.IsEnabled = play.IsEnabled = false;
                    state.Text = "Reading reply…";
                    result = "Read aloud stopped.";
                    pending = synth.SpeakAsync(text); // Plain text, never SSML.
                }
                catch (Exception)
                {
                    state.Text = result = "Speech playback failed.";
                    voice.IsEnabled = play.IsEnabled = true;
                }
            };
            stop.Click += (_, _) => dialog.Close();
            try { dialog.ShowDialog(); }
            finally
            {
                closed = true;
                synth.SpeakCompleted -= Completed;
                synth.SpeakAsyncCancelAll();
            }
            return result;
        }
        catch (Exception)
        {
            return "Installed Windows speech engine unavailable or playback failed.";
        }
    }
}
