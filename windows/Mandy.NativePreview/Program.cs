using System.IO;
using System.Diagnostics;
using System.Text;
using System.Windows.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Input;
using System.Windows.Automation;
using Microsoft.Win32;
using CharacterCompanion.Windows.Intelligence;
using CharacterCompanion.Windows.Speech;

namespace Mandy.NativePreview;

internal static class Program
{
    [STAThread]
    public static int Main()
    {
        // Use the framework's own Fluent resources rather than an additional UI package.
        AppContext.SetSwitch("Switch.System.Windows.Appearance.DisableFluentThemeWindowBackdrop", true);
        var app = new Application { ShutdownMode = ShutdownMode.OnMainWindowClose };
        try
        {
            if (!SystemParameters.HighContrast) app.Resources.MergedDictionaries.Add(new ResourceDictionary
            {
                Source = new Uri("pack://application:,,,/PresentationFramework.Fluent;component/Themes/Fluent.Dark.xaml")
            });
        }
        catch (Exception ex) when (ex is IOException or System.Windows.Markup.XamlParseException)
        {
            // Keep the stock WPF theme if the framework resource is unavailable.
        }
        var window = new ChatWindow();
        app.MainWindow = window;
        return app.Run(window);
    }
}

internal sealed class ChatWindow : Window
{
    private readonly TextBox folder = new() { IsReadOnly = true };
    private readonly TextBox name = new() { Text = "Mandy", MaxLength = 120 };
    private readonly TextBox identity = new() { Text = "You are Mandy. Converse naturally in English or Russian.", MaxLength = 8000, AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, Height = 68, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
    private readonly TextBox alias = new() { Text = "mandy-local", MaxLength = 256 };
    private readonly TextBox port = new() { Text = "8080", MaxLength = 5 };
    private readonly PasswordBox key = new() { MaxLength = 128 };
    private readonly TextBox draft = new() { MaxLength = 16000, AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, MinHeight = 76, MaxHeight = 144, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
    private readonly ListBox history = new() { HorizontalContentAlignment = HorizontalAlignment.Stretch };
    private readonly Border configuration = new() { Visibility = Visibility.Visible };
    private readonly TextBlock characterTitle = new() { Text = "No character selected", FontSize = 18, FontWeight = FontWeights.SemiBold, VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis };
    private readonly Button settingsButton = new() { Content = "Settings" };
    private readonly Button charactersButton = new() { Content = "Characters", IsEnabled = false };

    private readonly TextBox liveReply = new() { IsReadOnly = true, TextWrapping = TextWrapping.Wrap, MaxHeight = 160, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Visibility = Visibility.Collapsed, IsUndoEnabled = false, Margin = new Thickness(0, 0, 0, 8) };
    private readonly TextBlock status = new() { Text = "Choose an empty folder to create a workspace, or open an existing one.", TextWrapping = TextWrapping.Wrap };
    private readonly Button choose = new() { Content = "Choose folder" };
    private readonly Button create = new() { Content = "Create" };
    private readonly Button open = new() { Content = "Open" };
    private readonly Button send = new() { Content = "Send", IsEnabled = false };
    private readonly Button stop = new() { Content = "Stop", IsEnabled = false };
    private readonly Button refresh = new() { Content = "Refresh", IsEnabled = false };
    private readonly Button saveDraft = new() { Content = "Save draft", IsEnabled = false };
    private string lastSavedDraft = "";
    private readonly Button earlier = new() { Content = "Earlier messages", IsEnabled = false, Visibility = Visibility.Collapsed };
    private readonly Button dictate = new() { IsEnabled = false };
    private readonly Button keepSpeech = new() { Content = "Stop · keep draft" };
    private readonly Button sendSpeech = new() { Content = "Stop · send" };
    private readonly Button cancelSpeech = new() { Content = "Cancel" };
    private readonly TextBox whisperExe = new() { IsReadOnly = true };
    private readonly TextBox whisperModel = new() { IsReadOnly = true };
    private readonly ComboBox speechLanguage = new() { ItemsSource = new[] { "auto", "en", "ru" }, SelectedIndex = 0 };
    private readonly Button chooseSpeechExe = new() { Content = "Choose whisper-cli.exe" };
    private readonly Button chooseSpeechModel = new() { Content = "Choose speech model" };
    private FrameworkElement? speechActions;
    private FrameworkElement? normalMedia;
    private MicrophoneRecorder? recorder;
    private bool speechFinishing;
    private string recordingDraft = "";
    private int recordingStart, recordingLength;
    private string speechExePath = "", speechModelPath = "", recordingLanguage = "auto";
    private sealed record MessageRow(string Author, string Text, bool IsUser);
    private Workspace? workspace;
    private LocalHistory.Event? pending;
    private CancellationTokenSource? operation;
    private bool busy, closeRequested, historyValid;

    internal ChatWindow()
    {
        Title = "Mandy AI — Native Windows preview";
        Width = 920; Height = 800; MinWidth = 640; MinHeight = 590;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        UseLayoutRounding = true;
        FontSize = 15;
        if (SystemParameters.HighContrast)
        {
            SetResourceReference(BackgroundProperty, SystemColors.WindowBrushKey);
            SetResourceReference(ForegroundProperty, SystemColors.WindowTextBrushKey);
        }
        else if (Application.Current.Resources.MergedDictionaries.Count > 0)
        {
            Background = Brushes.Black;
            Foreground = Brushes.WhiteSmoke;
        }
        var layout = new DockPanel { Margin = new Thickness(18), LastChildFill = true };
        var header = new DockPanel { Margin = new Thickness(0,0,0,10), LastChildFill = true };
        DockPanel.SetDock(header, Dock.Top);
        layout.Children.Add(header);
        SetActionIcon(settingsButton, "settings", "Settings");
        var headerActions = Buttons(Unavailable("call", "Call", "Live voice calls are not implemented in this preview."), settingsButton);
        DockPanel.SetDock(headerActions, Dock.Right);
        header.Children.Add(headerActions);
        DockPanel.SetDock(charactersButton, Dock.Left);
        charactersButton.Margin = new Thickness(0,0,12,0);
        header.Children.Add(charactersButton);
        charactersButton.Click += async (_, _) => await ChooseCharacterAsync();
        charactersButton.Content = "Library";
        AutomationProperties.SetName(charactersButton, "Character and conversation library");
        header.Children.Add(characterTitle);
        var settings = new StackPanel();
        DockPanel.SetDock(configuration, Dock.Top);
        configuration.Child = new ScrollViewer { Content = settings, MaxHeight = 320, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
        layout.Children.Add(configuration);
        settings.Children.Add(new TextBlock { Text = "Workspace and local AI settings", FontSize = 18, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0,0,0,10) });
        settingsButton.Click += (_, _) => configuration.Visibility = configuration.Visibility == Visibility.Visible ? Visibility.Collapsed : Visibility.Visible;
        settings.Children.Add(Labeled("Local workspace", folder));
        settings.Children.Add(Buttons(choose, create, open));
        settings.Children.Add(Labeled("Character name (creation)", name));
        settings.Children.Add(Labeled("Character identity (creation)", identity));
        settings.Children.Add(Labeled("Trusted local server model alias", alias));
        settings.Children.Add(Labeled("Loopback port", port));
        settings.Children.Add(Labeled("Local server API key (not saved)", key));
        settings.Children.Add(Buttons(refresh, saveDraft));
        saveDraft.Click += async (_, _) => await SaveCurrentDraftAsync();
        settings.Children.Add(Labeled("Local speech executable", whisperExe));
        settings.Children.Add(Labeled("Local speech model", whisperModel));
        settings.Children.Add(Labeled("Dictation language", speechLanguage));
        settings.Children.Add(Buttons(chooseSpeechExe, chooseSpeechModel));
        chooseSpeechExe.Click += (_, _) => PickSpeechFile(true);
        chooseSpeechModel.Click += (_, _) => PickSpeechFile(false);
        var composer = new StackPanel { Margin = new Thickness(0,8,0,0), MaxWidth = 800 };
        DockPanel.SetDock(composer, Dock.Bottom);
        layout.Children.Add(composer);
        composer.Children.Add(liveReply);
        var composerContent = new StackPanel();
        var composerSurface = new Border { CornerRadius = new CornerRadius(22), BorderThickness = new Thickness(1), Padding = new Thickness(8), Child = composerContent };
        if (SystemParameters.HighContrast || Application.Current.Resources.MergedDictionaries.Count == 0)
        {
            composerSurface.SetResourceReference(Border.BackgroundProperty, SystemColors.WindowBrushKey);
            composerSurface.SetResourceReference(Border.BorderBrushProperty, SystemColors.WindowTextBrushKey);
        }
        else
        {
            composerSurface.Background = new SolidColorBrush(Color.FromRgb(24,24,24));
            composerSurface.BorderBrush = new SolidColorBrush(Color.FromRgb(66,66,66));
        }
        composer.Children.Add(composerSurface);
        var savedAppearance = ReadAppearance();
        var amoled = new CheckBox { Content = "Material AMOLED", IsChecked = savedAppearance.Black, Margin = new Thickness(0,8,0,8) };
        AutomationProperties.SetName(amoled, "Material AMOLED appearance (off: graphite dark)");
        amoled.IsEnabled = !SystemParameters.HighContrast && Application.Current.Resources.MergedDictionaries.Count > 0;
        var colors = DefaultColors(true);
        void ApplyColors(Dictionary<string, Color> values)
        {
            if (!amoled.IsEnabled || SystemParameters.HighContrast) return;
            colors = values;
            foreach (var entry in values)
                Resources["MandyColor_" + entry.Key] = Palette(entry.Value.R, entry.Value.G, entry.Value.B);
            Color accent = values["accent"];
            Color onAccent = Contrast(Colors.Black, accent) >= Contrast(Colors.White, accent) ? Colors.Black : Colors.White;
            Resources["MandyColor_onAccent"] = Palette(onAccent.R, onAccent.G, onAccent.B);
            SetResourceReference(BackgroundProperty, "MandyColor_canvas");
            SetResourceReference(ForegroundProperty, "MandyColor_text");
            composerSurface.SetResourceReference(Border.BackgroundProperty, "MandyColor_composer");
            composerSurface.SetResourceReference(Border.BorderBrushProperty, "MandyColor_outline");
        }
        void ApplyAppearance()
        {
            if (!amoled.IsEnabled || SystemParameters.HighContrast) return;
            var next = DefaultColors(amoled.IsChecked == true);
            if (WriteAppearance(amoled.IsChecked == true, next)) ApplyColors(next);
            else status.Text = "Could not save appearance. Existing colors retained.";
        }
        amoled.Checked += (_, _) => ApplyAppearance();
        amoled.Unchecked += (_, _) => ApplyAppearance();
        settings.Children.Add(amoled);
        var customColors = new Button { Content = "Custom colors…", IsEnabled = amoled.IsEnabled, Margin = new Thickness(0,0,0,8) };
        customColors.Click += (_, _) =>
        {
            if (SystemParameters.HighContrast) return;
            var edited = EditColors(colors, DefaultColors(amoled.IsChecked == true));
            if (edited != null)
            {
                if (WriteAppearance(amoled.IsChecked == true, edited)) ApplyColors(edited);
                else status.Text = "Could not save appearance. Existing colors retained.";
            }
        };
        settings.Children.Add(customColors);
        ApplyColors(savedAppearance.Values);
        draft.Padding = new Thickness(12,8,12,8);
        draft.FontSize = 16;
        draft.Background = Brushes.Transparent;
        draft.BorderThickness = new Thickness(0);
        composerContent.Children.Add(draft);
        var composerActions = new DockPanel { LastChildFill = false };
        SetActionIcon(send, "send", "Send message (Ctrl+Enter)");
        SetActionIcon(stop, "stop", "Stop generation (Escape)");
        var primaryActions = Buttons(send, stop);
        DockPanel.SetDock(primaryActions, Dock.Right);
        composerActions.Children.Add(primaryActions);
        SetActionIcon(dictate, "mic", "Dictate");
        var mediaActions = Buttons(Unavailable("attach", "Attach", "Multimodal attachments are not implemented in this preview."), dictate);
        normalMedia = mediaActions;
        speechActions = Buttons(cancelSpeech, keepSpeech, sendSpeech);
        speechActions.Visibility = Visibility.Collapsed;
        composerContent.Children.Add(speechActions);
        dictate.Click += (_, _) => StartDictation();
        keepSpeech.Click += async (_, _) => await EndDictationAsync(false, false);
        sendSpeech.Click += async (_, _) => await EndDictationAsync(true, false);
        cancelSpeech.Click += async (_, _) =>
        {
            if (speechFinishing) { operation?.Cancel(); cancelSpeech.IsEnabled = false; }
            else await EndDictationAsync(false, true);
        };
        DockPanel.SetDock(mediaActions, Dock.Left);
        composerActions.Children.Add(mediaActions);
        composerContent.Children.Add(composerActions);
        if (amoled.IsEnabled)
        {
            foreach (var button in headerActions.Children.OfType<Button>().Concat(mediaActions.Children.OfType<Button>()))
                button.SetResourceReference(Control.ForegroundProperty, "MandyColor_icons");
            foreach (var button in new[] { send, stop })
            {
                button.SetResourceReference(Control.BackgroundProperty, "MandyColor_accent");
                button.SetResourceReference(Control.ForegroundProperty, "MandyColor_onAccent");
            }
            draft.SetResourceReference(Control.ForegroundProperty, "MandyColor_text");
            liveReply.SetResourceReference(Control.ForegroundProperty, "MandyColor_text");
            liveReply.SetResourceReference(Control.BackgroundProperty, "MandyColor_composer");
            history.SetResourceReference(Control.ForegroundProperty, "MandyColor_text");
            status.SetResourceReference(TextBlock.ForegroundProperty, "MandyColor_muted");
        }
        status.FontSize = 12;
        status.Margin = new Thickness(8,4,8,0);
        composer.Children.Add(status);
        history.Background = Brushes.Transparent;
        history.BorderThickness = new Thickness(0);
        history.FontSize = 16;
        history.MaxWidth = 800;
        VirtualizingPanel.SetIsVirtualizing(history, true);
        VirtualizingPanel.SetVirtualizationMode(history, VirtualizationMode.Recycling);
        ScrollViewer.SetCanContentScroll(history, true);
        ScrollViewer.SetHorizontalScrollBarVisibility(history, ScrollBarVisibility.Disabled);
        // Preserve virtualization: bounded data, native text layout, no embedded browser.
        var template = new DataTemplate(typeof(MessageRow));
        var bubble = new FrameworkElementFactory(typeof(Border));
        bubble.SetValue(Border.CornerRadiusProperty, new CornerRadius(18));
        bubble.SetValue(Border.PaddingProperty, new Thickness(12,8,12,8));
        var stack = new FrameworkElementFactory(typeof(StackPanel));
        stack.SetValue(FrameworkElement.MarginProperty, new Thickness(8, 12, 8, 12));
        stack.SetValue(FrameworkElement.MaxWidthProperty, 760d);
        var author = new FrameworkElementFactory(typeof(TextBlock));
        author.SetBinding(TextBlock.TextProperty, new System.Windows.Data.Binding(nameof(MessageRow.Author)));
        author.SetValue(TextBlock.FontSizeProperty, 13d);
        author.SetValue(TextBlock.FontWeightProperty, FontWeights.SemiBold);
        author.SetValue(FrameworkElement.MarginProperty, new Thickness(0,0,0,6));
        stack.AppendChild(author);
        var text = new FrameworkElementFactory(typeof(TextBlock));
        text.SetBinding(TextBlock.TextProperty, new System.Windows.Data.Binding(nameof(MessageRow.Text)));
        text.SetValue(TextBlock.TextWrappingProperty, TextWrapping.Wrap);
        text.SetValue(TextBlock.LineHeightProperty, 24d);
        stack.AppendChild(text);
        bubble.Name = "MessageSurface";
        bubble.AppendChild(stack);
        template.VisualTree = bubble;
        if (amoled.IsEnabled)
        {
            author.SetResourceReference(TextBlock.ForegroundProperty, "MandyColor_text");
            text.SetResourceReference(TextBlock.ForegroundProperty, "MandyColor_text");
        }
        if (!SystemParameters.HighContrast && Application.Current.Resources.MergedDictionaries.Count > 0)
        {
            var bubbleTrigger = new DataTrigger { Binding = new System.Windows.Data.Binding(nameof(MessageRow.IsUser)), Value = true };
            bubbleTrigger.Setters.Add(new Setter(Border.BackgroundProperty, new DynamicResourceExtension("MandyColor_bubble"), "MessageSurface"));
            template.Triggers.Add(bubbleTrigger);
        }
        history.ItemTemplate = template;
        var items = new Style(typeof(ListBoxItem));
        items.Setters.Add(new Setter(Control.HorizontalContentAlignmentProperty, HorizontalAlignment.Stretch));
        var user = new DataTrigger { Binding = new System.Windows.Data.Binding(nameof(MessageRow.IsUser)), Value = true };
        user.Setters.Add(new Setter(Control.HorizontalContentAlignmentProperty, HorizontalAlignment.Right));
        user.Setters.Add(new Setter(FrameworkElement.MarginProperty, new Thickness(56,0,0,0)));
        items.Triggers.Add(user);
        history.ItemContainerStyle = items;
        var historyArea = new DockPanel { LastChildFill = true, MaxWidth = 800 };
        DockPanel.SetDock(earlier, Dock.Top);
        earlier.HorizontalAlignment = HorizontalAlignment.Center;
        earlier.Margin = new Thickness(0,4,0,4);
        historyArea.Children.Add(earlier);
        historyArea.Children.Add(history);
        layout.Children.Add(historyArea);
        earlier.Click += (_, _) => BrowseHistory();
        Content = layout;
        AutomationProperties.SetName(draft, "Message draft");
        AutomationProperties.SetName(history, "Conversation history");
        var copyMessage = new MenuItem { Header = "Copy selected message" };
        var historyMenu = new ContextMenu();
        historyMenu.Items.Add(copyMessage);
        history.ContextMenu = historyMenu;
        historyMenu.Opened += (_, _) => copyMessage.IsEnabled = history.SelectedItem is MessageRow;
        void CopySelected()
        {
            if (history.SelectedItem is not MessageRow row) return;
            try { Clipboard.SetText(row.Text); status.Text = "Selected message copied."; }
            catch (System.Runtime.InteropServices.ExternalException) { status.Text = "Clipboard unavailable."; }
        }
        copyMessage.Click += (_, _) => CopySelected();
        history.PreviewKeyDown += (_, e) =>
        {
            if (e.Key == Key.C && Keyboard.Modifiers == ModifierKeys.Control && history.SelectedItem is MessageRow)
            { e.Handled = true; CopySelected(); }
        };
        AutomationProperties.SetName(liveReply, "Incomplete streamed reply");
        AutomationProperties.SetName(key, "Local server API key");
        AutomationProperties.SetName(settingsButton, "Show or hide settings");
        draft.TextChanged += (_, _) => UpdateSendAvailability();
        draft.PreviewKeyDown += async (_, e) =>
        {
            if (e.Key == Key.S && Keyboard.Modifiers == ModifierKeys.Control && saveDraft.IsEnabled)
            {
                e.Handled = true;
                await SaveCurrentDraftAsync();
            }
            else if (e.Key == Key.Enter && Keyboard.Modifiers == ModifierKeys.Control && send.IsEnabled)
            {
                e.Handled = true;
                await SendAsync();
            }
        };
        PreviewKeyDown += (_, e) =>
        {
            if (e.Key == Key.Escape && recorder != null && cancelSpeech.IsEnabled)
            {
                e.Handled = true;
                cancelSpeech.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
            }
            else if (e.Key == Key.Escape && stop.IsEnabled)
            {
                e.Handled = true;
                stop.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
            }
        };
        choose.Click += (_, _) =>
        {
            var picker = new OpenFolderDialog { Title = "Choose local Mandy workspace folder" };
            if (picker.ShowDialog(this) == true) folder.Text = picker.FolderName;
        };
        create.Click += async (_, _) => await OpenAsync(true);
        open.Click += async (_, _) => await OpenAsync(false);
        refresh.Click += async (_, _) => await RefreshAsync();
        send.Click += async (_, _) => await SendAsync();
        stop.Click += (_, _) => { operation?.Cancel(); status.Text = "Stopping…"; stop.IsEnabled = false; };
        Closing += (_, e) =>
        {
            if (busy)
            {
                e.Cancel = true; closeRequested = true; operation?.Cancel();
                if (recorder != null && !speechFinishing)
                    Dispatcher.InvokeAsync(async () => await EndDictationAsync(false, true));
                status.Text = "Finishing the active operation before closing…";
                return;
            }
            if (workspace != null && pending == null && draft.Text != lastSavedDraft &&
                MessageBox.Show(this, "Close without saving changes to this draft?", "Unsaved draft", MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes)
            {
                e.Cancel = true; closeRequested = false; return;
            }
            workspace?.Dispose();
        };
        UpdateControls();
    }

    // Matching original line icons with native vector rendering and inherited theme color.
    private static readonly (string Key, string Label)[] ColorRoles =
    {
        ("canvas", "Chat background"), ("composer", "Composer surface"), ("bubble", "Your message bubble"),
        ("text", "Message text"), ("muted", "Secondary text"), ("accent", "Send button"),
        ("outline", "Composer border"), ("icons", "Action icons")
    };
    private static Dictionary<string, Color> DefaultColors(bool black)
    {
        string[] values = black
            ? new[] { "#000000", "#141414", "#232323", "#F2F2F2", "#B5B5B5", "#F2F2F2", "#3D3D3D", "#CCCCCC" }
            : new[] { "#212121", "#303030", "#383838", "#F2F2F2", "#B5B5B5", "#F2F2F2", "#555555", "#CCCCCC" };
        return ColorRoles.Select((role, i) => (role.Key, Value: (Color)ColorConverter.ConvertFromString(values[i])))
            .ToDictionary(x => x.Key, x => x.Value);
    }
    private static double Contrast(Color a, Color b)
    {
        static double Linear(byte n) { double v = n / 255.0; return v <= .04045 ? v / 12.92 : Math.Pow((v + .055) / 1.055, 2.4); }
        static double L(Color c) => .2126 * Linear(c.R) + .7152 * Linear(c.G) + .0722 * Linear(c.B);
        double x = L(a), y = L(b);
        return (Math.Max(x, y) + .05) / (Math.Min(x, y) + .05);
    }
    private static string? ColorError(Dictionary<string, Color> candidate)
    {
        if (ColorRoles.Any(role => !candidate.ContainsKey(role.Key))) return "Missing color role.";
        foreach (string surface in new[] { "canvas", "composer", "bubble" })
            if (Contrast(candidate["text"], candidate[surface]) < 4.5)
                return "Message text requires 4.5:1 contrast against " + surface;
        foreach (string surface in new[] { "canvas", "composer" })
        {
            if (Contrast(candidate["muted"], candidate[surface]) < 4.5)
                return "Secondary text requires 4.5:1 contrast against " + surface;
            if (Contrast(candidate["icons"], candidate[surface]) < 3.0)
                return "Icons require 3:1 contrast against " + surface;
        }
        if (Contrast(candidate["accent"], candidate["composer"]) < 3.0)
            return "Send button requires 3:1 contrast against composer.";
        return null;
    }

    // One small, versioned per-user registry value, only colors. No credentials or chat data.
    private const string AppearanceKey = @"Software\MandyAI\NativePreview";
    private static (bool Black, Dictionary<string, Color> Values) ReadAppearance()
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(AppearanceKey, false);
            var raw = key?.GetValue("AppearanceV1") as string;
            if (raw != null && raw.Length <= 100)
            {
                var parts = raw.Split('|');
                if (parts.Length == 10 && parts[0] == "1" && (parts[1] == "0" || parts[1] == "1") &&
                    parts.Skip(2).All(value => System.Text.RegularExpressions.Regex.IsMatch(value, @"\A#[0-9a-fA-F]{6}\z")))
                {
                    var values = ColorRoles.Select((role, i) =>
                        (role.Key, Value: (Color)ColorConverter.ConvertFromString(parts[i+2])))
                        .ToDictionary(x => x.Key, x => x.Value);
                    if (ColorError(values) == null) return (parts[1] == "1", values);
                }
            }
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Security.SecurityException or FormatException or ArgumentException) { }
        return (true, DefaultColors(true));
    }
    private static bool WriteAppearance(bool black, Dictionary<string, Color> values)
    {
        if (ColorError(values) != null) return false;
        try
        {
            using var key = Registry.CurrentUser.CreateSubKey(AppearanceKey, true);
            if (key == null) return false;
            string raw = "1|" + (black ? "1" : "0") + "|" + string.Join("|", ColorRoles.Select(role =>
            {
                Color c = values[role.Key];
                return $"#{c.R:X2}{c.G:X2}{c.B:X2}";
            }));
            key.SetValue("AppearanceV1", raw, RegistryValueKind.String);
            return true;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Security.SecurityException or ArgumentException) { return false; }
    }

    private static string EncodeTheme(Dictionary<string, Color> values) =>
        System.Text.Json.JsonSerializer.Serialize(new
        {
            schema = 1,
            name = "Custom",
            colors = ColorRoles.ToDictionary(role => role.Key, role =>
            {
                Color c = values[role.Key];
                return $"#{c.R:X2}{c.G:X2}{c.B:X2}";
            })
        }, new System.Text.Json.JsonSerializerOptions { WriteIndented = true });

    private static Dictionary<string, Color>? DecodeTheme(string raw)
    {
        if (raw.Length is < 1 or > 2048) return null;
        try
        {
            using var document = System.Text.Json.JsonDocument.Parse(raw, new System.Text.Json.JsonDocumentOptions { MaxDepth = 4 });
            var root = document.RootElement;
            if (root.ValueKind != System.Text.Json.JsonValueKind.Object ||
                !root.EnumerateObject().Select(p => p.Name).OrderBy(x => x, StringComparer.Ordinal)
                    .SequenceEqual(new[] { "colors", "name", "schema" }) ||
                !root.GetProperty("schema").TryGetInt32(out int version) || version != 1 ||
                root.GetProperty("name").GetString() is not { Length: > 0 and <= 80 }) return null;
            var colors = root.GetProperty("colors");
            if (colors.ValueKind != System.Text.Json.JsonValueKind.Object ||
                !colors.EnumerateObject().Select(p => p.Name).OrderBy(x => x, StringComparer.Ordinal)
                    .SequenceEqual(ColorRoles.Select(r => r.Key).OrderBy(x => x, StringComparer.Ordinal))) return null;
            var result = new Dictionary<string, Color>();
            foreach (var role in ColorRoles)
            {
                var value = colors.GetProperty(role.Key).GetString();
                if (value == null || !System.Text.RegularExpressions.Regex.IsMatch(value, @"\A#[0-9a-fA-F]{6}\z")) return null;
                result.Add(role.Key, (Color)ColorConverter.ConvertFromString(value));
            }
            return ColorError(result) == null ? result : null;
        }
        catch (Exception ex) when (ex is System.Text.Json.JsonException or InvalidOperationException or FormatException or ArgumentException) { return null; }
    }

    private Dictionary<string, Color>? EditColors(Dictionary<string, Color> existing, Dictionary<string, Color> defaults)
    {
        var panel = new StackPanel { Margin = new Thickness(16) };
        var dialog = new Window { Owner = this, Title = "Custom colors", Width = 450, Height = 590,
            WindowStartupLocation = WindowStartupLocation.CenterOwner, ShowInTaskbar = false,
            Content = new ScrollViewer { Content = panel, VerticalScrollBarVisibility = ScrollBarVisibility.Auto } };
        var fields = new Dictionary<string, TextBox>();
        string Hex(Color c) => $"#{c.R:X2}{c.G:X2}{c.B:X2}";
        foreach (var role in ColorRoles)
        {
            var box = new TextBox { Text = Hex(existing[role.Key]), MaxLength = 7 };
            AutomationProperties.SetName(box, role.Label);
            fields.Add(role.Key, box);
            panel.Children.Add(Labeled(role.Label, box));
        }
        var warning = new TextBlock { Text = "Use #RRGGBB. Contrast is checked before applying.", TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0,12,0,8) };
        panel.Children.Add(warning);
        var copyTheme = new Button { Content = "Copy theme JSON" };
        var importTheme = new Button { Content = "Import theme JSON" };
        panel.Children.Add(Buttons(copyTheme, importTheme));
        var reset = new Button { Content = "Reset fields" };
        var apply = new Button { Content = "Apply", IsDefault = true };
        var cancel = new Button { Content = "Cancel", IsCancel = true };
        panel.Children.Add(Buttons(reset, apply, cancel));
        Dictionary<string, Color>? result = null;
        reset.Click += (_, _) => { foreach (var field in fields) field.Value.Text = Hex(defaults[field.Key]); };
        Dictionary<string, Color>? Candidate()
        {
            var candidate = new Dictionary<string, Color>();
            foreach (var field in fields)
            {
                if (!System.Text.RegularExpressions.Regex.IsMatch(field.Value.Text, @"\A#[0-9a-fA-F]{6}\z"))
                { warning.Text = "Enter a six-digit hexadecimal color."; field.Value.Focus(); return null; }
                candidate.Add(field.Key, (Color)ColorConverter.ConvertFromString(field.Value.Text));
            }
            var error = ColorError(candidate);
            if (error != null) { warning.Text = error; return null; }
            return candidate;
        }
        copyTheme.Click += (_, _) =>
        {
            var candidate = Candidate();
            if (candidate == null) return;
            try { Clipboard.SetText(EncodeTheme(candidate)); warning.Text = "Theme copied. No chat or account data included."; }
            catch (System.Runtime.InteropServices.ExternalException) { warning.Text = "Clipboard unavailable."; }
        };
        importTheme.Click += (_, _) =>
        {
            var input = new TextBox { AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, MaxLength = 2048, MinHeight = 180, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
            AutomationProperties.SetName(input, "Paste theme JSON");
            var importPanel = new StackPanel { Margin = new Thickness(16) };
            importPanel.Children.Add(input);
            var error = new TextBlock { TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0,8,0,0) };
            importPanel.Children.Add(error);
            var load = new Button { Content = "Load fields", IsDefault = true };
            var back = new Button { Content = "Cancel", IsCancel = true };
            importPanel.Children.Add(Buttons(load, back));
            var importer = new Window { Owner = dialog, Title = "Import theme JSON", Width = 430, SizeToContent = SizeToContent.Height, WindowStartupLocation = WindowStartupLocation.CenterOwner, ShowInTaskbar = false, Content = importPanel };
            load.Click += (_, _) =>
            {
                var candidate = DecodeTheme(input.Text);
                if (candidate == null) { error.Text = "Invalid theme or insufficient contrast."; return; }
                foreach (var field in fields) field.Value.Text = Hex(candidate[field.Key]);
                warning.Text = "Theme loaded into fields. Apply to save.";
                importer.DialogResult = true;
            };
            importer.ShowDialog();
        };
        apply.Click += (_, _) =>
        {
            var candidate = Candidate();
            if (candidate == null) return;
            result = candidate; dialog.DialogResult = true;
        };
        return dialog.ShowDialog() == true ? result : null;
    }

    private static SolidColorBrush Palette(byte red, byte green, byte blue)
    {
        var brush = new SolidColorBrush(Color.FromRgb(red, green, blue));
        brush.Freeze();
        return brush;
    }

    private static void SetActionIcon(Button button, string kind, string description)
    {
        var geometry = Geometry.Parse(kind switch
        {
            "settings" => "M4,6 L6,6 M10,6 L20,6 M8,4 A2,2 0 1 1 8,8 A2,2 0 1 1 8,4 M4,12 L14,12 M18,12 L20,12 M16,10 A2,2 0 1 1 16,14 A2,2 0 1 1 16,10 M4,18 L8,18 M12,18 L20,18 M10,16 A2,2 0 1 1 10,20 A2,2 0 1 1 10,16",
            "call" => "M4,4 L8,3 L11,8 L8,11 Q10,15 14,17 L17,14 L21,17 L20,21 C10,21 3,14 4,4 Z",
            "attach" => "M8,13 L14,7 C17,4 21,8 18,11 L10,19 C5,24 -1,17 4,12 L13,3",
            "mic" => "M9,6 A3,3 0 0 1 15,6 L15,11 A3,3 0 0 1 9,11 Z M5,11 A7,7 0 0 0 19,11 M12,18 L12,21 M8,21 L16,21",
            "send" => "M12,20 L12,4 M5,11 L12,4 L19,11",
            "retry" => "M20,9 L20,3 M20,9 L14,9 M20,9 A8,8 0 1 0 20,15",
            "stop" => "M6,6 L18,6 L18,18 L6,18 Z",
            _ => throw new ArgumentException("Unknown action icon.")
        });
        if (geometry.CanFreeze) geometry.Freeze();
        var path = new System.Windows.Shapes.Path
        {
            Data = geometry, StrokeThickness = 1.8, StrokeStartLineCap = PenLineCap.Round,
            StrokeEndLineCap = PenLineCap.Round, StrokeLineJoin = PenLineJoin.Round,
            Width = 22, Height = 22, Stretch = Stretch.Uniform, IsHitTestVisible = false
        };
        path.SetBinding(System.Windows.Shapes.Shape.StrokeProperty,
            new System.Windows.Data.Binding(nameof(Control.Foreground)) { Source = button });
        button.Content = path;
        button.MinWidth = 44; button.MinHeight = 44;
        button.ToolTip = description;
        AutomationProperties.SetName(button, description);
    }

    private static Button Unavailable(string kind, string label, string reason)
    {
        var button = new Button { IsEnabled = false };
        SetActionIcon(button, kind, label);
        button.ToolTip = reason;
        ToolTipService.SetShowOnDisabled(button, true);
        AutomationProperties.SetName(button, label);
        AutomationProperties.SetHelpText(button, reason);
        return button;
    }

    private static FrameworkElement Labeled(string label, Control field)
    {
        var panel = new DockPanel { Margin = new Thickness(0,2,0,2) };
        var title = new TextBlock { Text = label, Width = 218, VerticalAlignment = VerticalAlignment.Center, TextWrapping = TextWrapping.Wrap };
        DockPanel.SetDock(title, Dock.Left); panel.Children.Add(title);
        field.MinHeight = 27; panel.Children.Add(field);
        return panel;
    }
    private static WrapPanel Buttons(params Button[] buttons)
    {
        var panel = new WrapPanel { Margin = new Thickness(0,5,0,5) };
        foreach (var button in buttons)
        {
            button.Padding = new Thickness(14,6,14,6);
            button.Margin = new Thickness(0,0,8,0);
            panel.Children.Add(button);
        }
        return panel;
    }
    private void UpdateControls()
    {
        bool ready = workspace != null && historyValid && !busy;
        choose.IsEnabled = create.IsEnabled = open.IsEnabled = !busy && workspace == null;
        name.IsEnabled = identity.IsEnabled = !busy && workspace == null;
        alias.IsEnabled = port.IsEnabled = key.IsEnabled = !busy;
        draft.IsEnabled = ready && pending == null;
        UpdateSendAvailability();
        string action = pending != null ? "Retry pending message (Ctrl+Enter)" : "Send message (Ctrl+Enter)";
        if (AutomationProperties.GetName(send) != action)
            SetActionIcon(send, pending != null ? "retry" : "send", action);
        refresh.IsEnabled = workspace != null && !busy;
        saveDraft.IsEnabled = ready && pending == null;
        earlier.IsEnabled = ready;
        charactersButton.IsEnabled = ready && !closeRequested;

        bool recording = recorder != null;
        chooseSpeechExe.IsEnabled = chooseSpeechModel.IsEnabled = speechLanguage.IsEnabled = !busy;
        dictate.IsEnabled = ready && pending == null;
        if (normalMedia != null) normalMedia.Visibility = recording ? Visibility.Collapsed : Visibility.Visible;
        if (speechActions != null) speechActions.Visibility = recording ? Visibility.Visible : Visibility.Collapsed;
        keepSpeech.IsEnabled = sendSpeech.IsEnabled = recording && !speechFinishing;
        cancelSpeech.IsEnabled = recording && operation?.IsCancellationRequested != true;
        stop.IsEnabled = !recording && busy && operation != null && !operation.IsCancellationRequested;
        stop.Visibility = !recording && busy && operation != null ? Visibility.Visible : Visibility.Collapsed;
        send.Visibility = recording || (busy && operation != null) ? Visibility.Collapsed : Visibility.Visible;
    }
    private void UpdateSendAvailability() => send.IsEnabled = workspace != null && historyValid && !busy &&
        (pending != null || !string.IsNullOrWhiteSpace(draft.Text));
    private void Finish()
    {
        operation?.Dispose(); operation = null; busy = false; UpdateControls();
        if (closeRequested) Close();
    }
    private void ShowRows(List<LocalHistory.Event> rows)
    {
        var w = workspace ?? throw new InvalidOperationException();
        pending = rows.FirstOrDefault() is { Role: "user" } user ? user : null;
        history.ItemsSource = rows.AsEnumerable().Reverse().Select(e =>
            new MessageRow(e.Role == "user" ? "You" : w.Character.Name, e.Text, e.Role == "user")).ToArray();
        if (history.Items.Count > 0) history.ScrollIntoView(history.Items[history.Items.Count - 1]);
        historyValid = true;
        earlier.Visibility = rows.Count == 50 ? Visibility.Visible : Visibility.Collapsed;
        if (pending != null) draft.Text = pending.Text;
    }
    private void BrowseHistory()
    {
        var w = workspace;
        if (busy || !historyValid || w == null || closeRequested) return;
        // Modal, read-only browser: never change the main composer, retry state,
        // or active conversation while viewing historical pages.
        var list = new ListBox
        {
            ItemTemplate = history.ItemTemplate, ItemContainerStyle = history.ItemContainerStyle,
            HorizontalContentAlignment = HorizontalAlignment.Stretch,
            Background = Brushes.Transparent, BorderThickness = new Thickness(0), FontSize = 16
        };
        VirtualizingPanel.SetIsVirtualizing(list, true);
        VirtualizingPanel.SetVirtualizationMode(list, VirtualizationMode.Recycling);
        ScrollViewer.SetCanContentScroll(list, true);
        ScrollViewer.SetHorizontalScrollBarVisibility(list, ScrollBarVisibility.Disabled);
        var older = new Button { Content = "Older", IsEnabled = false };
        var latest = new Button { Content = "Latest", IsEnabled = false };
        var copy = new Button { Content = "Copy selected message", IsEnabled = false };
        var close = new Button { Content = "Close", IsCancel = true };
        var pageStatus = new TextBlock { Text = "Opening history…", TextWrapping = TextWrapping.Wrap };
        var controls = new StackPanel { Margin = new Thickness(8) };
        controls.Children.Add(Buttons(latest, older, copy, close));
        controls.Children.Add(pageStatus);
        var panel = new DockPanel { Margin = new Thickness(12), LastChildFill = true };
        DockPanel.SetDock(controls, Dock.Top);
        panel.Children.Add(controls);
        panel.Children.Add(list);
        var dialog = new Window
        {
            Owner = this, Title = "Conversation history — " + w.Character.Name,
            Width = 760, Height = 650, MinWidth = 420, MinHeight = 360,
            WindowStartupLocation = WindowStartupLocation.CenterOwner, ShowInTaskbar = false,
            Content = panel
        };
        // Reused message template resolves dynamic colors within this window.
        if (!SystemParameters.HighContrast)
        {
            foreach (var k in new[] { "canvas", "text", "bubble" })
                if (Resources.Contains("MandyColor_" + k))
                    dialog.Resources["MandyColor_" + k] = Resources["MandyColor_" + k];
            if (dialog.Resources.Contains("MandyColor_canvas"))
                dialog.SetResourceReference(BackgroundProperty, "MandyColor_canvas");
            if (dialog.Resources.Contains("MandyColor_text"))
            {
                dialog.SetResourceReference(ForegroundProperty, "MandyColor_text");
                list.SetResourceReference(Control.ForegroundProperty, "MandyColor_text");
            }
        }
        bool loading = false, closed = false;
        int page = 0;
        (long Clock, string Id)? cursor = null;
        dialog.Closing += (_, e) => { if (loading) e.Cancel = true; };
        dialog.Closed += (_, _) => closed = true;
        async Task LoadPage(bool reset)
        {
            if (loading || closed || (!reset && cursor == null)) return;
            loading = true;
            older.IsEnabled = latest.IsEnabled = copy.IsEnabled = close.IsEnabled = false;
            try
            {
                var rows = await Task.Run(() => w.Page(reset ? null : cursor));
                if (closed) return;
                if (rows.Count == 0 && !reset)
                {
                    cursor = null;
                    pageStatus.Text = "No earlier messages.";
                    return;
                }
                page = reset ? 1 : page + 1;
                cursor = rows.Count == 50 ? (rows[^1].Clock, rows[^1].Id) : null;
                list.ItemsSource = rows.AsEnumerable().Reverse().Select(row =>
                    new MessageRow(row.Role == "user" ? "You" : w.Character.Name, row.Text, row.Role == "user")).ToArray();
                pageStatus.Text = $"Page {page} · {rows.Count} messages";
            }
            catch (Exception) { pageStatus.Text = "History could not be read. Saved messages were not changed."; }
            finally
            {
                loading = false;
                if (!closed)
                {
                    latest.IsEnabled = close.IsEnabled = true;
                    older.IsEnabled = cursor != null;
                    copy.IsEnabled = list.SelectedItem is MessageRow;
                }
            }
        }
        list.SelectionChanged += (_, _) => copy.IsEnabled = !loading && list.SelectedItem is MessageRow;
        copy.Click += (_, _) =>
        {
            if (list.SelectedItem is not MessageRow row) return;
            try { Clipboard.SetText(row.Text); pageStatus.Text = "Selected message copied."; }
            catch (System.Runtime.InteropServices.ExternalException) { pageStatus.Text = "Clipboard unavailable."; }
        };
        latest.Click += async (_, _) => await LoadPage(true);
        older.Click += async (_, _) => await LoadPage(false);
        dialog.Loaded += async (_, _) => await LoadPage(true);
        dialog.ShowDialog();
    }

    private async Task OpenAsync(bool isNew)
    {
        if (busy || workspace != null) return;
        if (isNew && MessageBox.Show(this, "Create a new local workspace in the selected empty folder? The history is not encrypted at rest.",
                "Mandy AI", MessageBoxButton.OKCancel, MessageBoxImage.Question) != MessageBoxResult.OK) return;
        var path = folder.Text; var characterName = name.Text; var characterIdentity = identity.Text;
        busy = true; UpdateControls();
        try
        {
            workspace = await Task.Run(() => Workspace.Open(path, isNew, characterName, characterIdentity));
            name.Text = workspace.Character.Name; identity.Text = workspace.Character.Identity;
            var opened = workspace;
            ShowRows(await Task.Run(opened.Recent));
            lastSavedDraft = await Task.Run(opened.ReadDraft);
            if (pending == null) draft.Text = lastSavedDraft;
            configuration.Visibility = Visibility.Collapsed;
            characterTitle.Text = workspace.Character.Name;
            charactersButton.ToolTip = workspace.ConversationTitle;
            status.Text = pending == null ? "Local workspace ready." : "A saved user message is awaiting a reply.";
        }
        catch (Exception)
        {
            workspace?.Dispose(); workspace = null; historyValid = false;
            status.Text = "Workspace open failed. Existing files were not reset.";
        }
        finally { Finish(); }
    }
    private async Task ChooseCharacterAsync()
    {
        var w = workspace;
        if (busy || !historyValid || w == null || closeRequested) return;
        var options = w.Profiles.ToArray();
        var list = new ListBox { ItemsSource = options, DisplayMemberPath = nameof(Workspace.Profile.Name), MinHeight = 160, MaxHeight = 300 };
        list.SelectedItem = options.Single(p => p.Character == w.Character.Character);
        var select = new Button { Content = "Open character", IsDefault = true };
        var add = new Button { Content = "Create character", IsEnabled = options.Length < 64 };
        var cancel = new Button { Content = "Cancel", IsCancel = true };
        var panel = new StackPanel { Margin = new Thickness(16) };
        panel.Children.Add(list);
        var chats = new Button { Content = "Current character conversations" };
        var edit = new Button { Content = "Edit current character", IsEnabled = pending == null };
        panel.Children.Add(Buttons(select, add, cancel));
        panel.Children.Add(Buttons(chats, edit));
        var dialog = new Window { Owner = this, Title = "Characters", Width = 430, SizeToContent = SizeToContent.Height,
            WindowStartupLocation = WindowStartupLocation.CenterOwner, ShowInTaskbar = false, Content = panel };
        Workspace.Profile? selected = null;
        bool creating = false, browseChats = false, editing = false;
        edit.Click += (_, _) => { editing = true; dialog.DialogResult = true; };
        chats.Click += (_, _) => { browseChats = true; dialog.DialogResult = true; };
        select.Click += (_, _) => { selected = list.SelectedItem as Workspace.Profile; if (selected != null) dialog.DialogResult = true; };
        add.Click += (_, _) => { creating = true; dialog.DialogResult = true; };
        if (dialog.ShowDialog() != true) return;
        if (browseChats) { await ChooseConversationAsync(); return; }
        if (editing) { await EditCurrentCharacterAsync(); return; }
        string newName = "", newIdentity = "";
        if (creating)
        {
            var n = new TextBox { MaxLength = 120 };
            var i = new TextBox { MaxLength = 8000, AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, MinHeight = 120, MaxHeight = 200, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
            var createPanel = new StackPanel { Margin = new Thickness(16) };
            createPanel.Children.Add(Labeled("Character name", n));
            createPanel.Children.Add(Labeled("Character identity", i));
            var createButton = new Button { Content = "Create", IsDefault = true };
            var back = new Button { Content = "Cancel", IsCancel = true };
            createPanel.Children.Add(Buttons(createButton, back));
            var creator = new Window { Owner = this, Title = "New character", Width = 460, SizeToContent = SizeToContent.Height,
                WindowStartupLocation = WindowStartupLocation.CenterOwner, ShowInTaskbar = false, Content = createPanel };
            createButton.Click += (_, _) =>
            {
                try { LocalHistory.ValidateText(n.Text); LocalHistory.ValidateText(i.Text); creator.DialogResult = true; }
                catch (ArgumentException) { MessageBox.Show(creator, "Enter a valid name and identity.", "Mandy AI"); }
            };
            if (creator.ShowDialog() != true) return;
            newName = n.Text; newIdentity = i.Text;
        }
        else if (selected == null || selected.Character == w.Character.Character) return;
        var previous = w.Character;
        var previousPending = pending;
        var previousRows = history.ItemsSource;
        string previousDraft = draft.Text, previousSaved = lastSavedDraft;
        bool saveOld = pending == null && previousDraft != previousSaved;
        busy = true; UpdateControls();
        try
        {
            var loaded = await Task.Run(() =>
            {
                if (saveOld) w.SaveDraft(previousDraft);
                var target = creating ? w.AddCharacter(newName, newIdentity) : selected!;
                try
                {
                    w.SelectCharacter(target.Character);
                    var rows = w.Recent(); var savedDraft = w.ReadDraft();
                    w.SaveSelection();
                    return (Rows: rows, Draft: savedDraft);
                }
                catch { w.SelectConversation(previous.Character, previous.Chat); throw; }
            });
            draft.Text = loaded.Draft;
            lastSavedDraft = loaded.Draft;
            ShowRows(loaded.Rows);
            name.Text = w.Character.Name;
            identity.Text = w.Character.Identity;
            characterTitle.Text = w.Character.Name;
            charactersButton.ToolTip = w.ConversationTitle;
            status.Text = pending == null ? "Character opened." : "Character opened · pending reply recovered.";
        }
        catch (Exception)
        {
            // Do not render another character's history alongside the old draft.
            w.SelectConversation(previous.Character, previous.Chat);
            charactersButton.ToolTip = w.ConversationTitle;
            historyValid = false;
            pending = previousPending;
            history.ItemsSource = previousRows;
            draft.Text = previousDraft;
            lastSavedDraft = previousSaved;
            name.Text = previous.Name;
            identity.Text = previous.Identity;
            characterTitle.Text = previous.Name;
            status.Text = "Character operation failed. Refresh the original conversation before continuing.";
        }
        finally { Finish(); }
    }

    private async Task EditCurrentCharacterAsync()
    {
        var w = workspace;
        if (busy || !historyValid || w == null || closeRequested || pending != null) return;
        var original = w.Character;
        var n = new TextBox { Text = original.Name, MaxLength = 120 };
        var i = new TextBox { Text = original.Identity, MaxLength = 8000, AcceptsReturn = true,
            TextWrapping = TextWrapping.Wrap, MinHeight = 160, MaxHeight = 280, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
        var panel = new StackPanel { Margin = new Thickness(16) };
        panel.Children.Add(new TextBlock { Text = "Changes affect future replies in all conversations with this character. Existing messages and drafts remain unchanged.", TextWrapping = TextWrapping.Wrap });
        panel.Children.Add(Labeled("Character name", n));
        panel.Children.Add(Labeled("Character identity", i));
        var apply = new Button { Content = "Save changes", IsDefault = true };
        var cancel = new Button { Content = "Cancel", IsCancel = true };
        panel.Children.Add(Buttons(apply, cancel));
        var dialog = new Window { Owner = this, Title = "Edit character", Width = 480, SizeToContent = SizeToContent.Height,
            WindowStartupLocation = WindowStartupLocation.CenterOwner, ShowInTaskbar = false, Content = panel };
        apply.Click += (_, _) =>
        {
            try { LocalHistory.ValidateText(n.Text); LocalHistory.ValidateText(i.Text); dialog.DialogResult = true; }
            catch (ArgumentException) { MessageBox.Show(dialog, "Enter a valid name and identity.", "Mandy AI"); }
        };
        if (dialog.ShowDialog() != true) return;
        string newName = n.Text, newIdentity = i.Text;
        if (newName == original.Name && newIdentity == original.Identity) return;
        busy = true; UpdateControls();
        try
        {
            await Task.Run(() => w.EditCharacter(original.Character, newName, newIdentity));
            name.Text = w.Character.Name;
            identity.Text = w.Character.Identity;
            characterTitle.Text = w.Character.Name;
            // Only display labels change; do not reload or replace the user's editable draft.
            if (history.ItemsSource is IEnumerable<MessageRow> rows)
                history.ItemsSource = rows.Select(row => row.IsUser ? row : row with { Author = w.Character.Name }).ToArray();
            status.Text = "Character saved.";
        }
        catch (Exception) { status.Text = "Character edit failed. Reopen the workspace if the view is stale."; }
        finally { Finish(); }
    }

    private async Task ChooseConversationAsync()
    {
        var w = workspace;
        if (busy || !historyValid || w == null || closeRequested) return;
        var options = w.Conversations;
        var list = new ListBox { ItemsSource = options, DisplayMemberPath = nameof(Workspace.Conversation.Title), MinHeight = 140, MaxHeight = 300 };
        list.SelectedItem = options.Single(c => c.Chat == w.Character.Chat);
        var openChat = new Button { Content = "Open conversation", IsDefault = true };
        var addChat = new Button { Content = "New conversation" };
        var renameChat = new Button { Content = "Rename selected" };
        var cancel = new Button { Content = "Cancel", IsCancel = true };
        var title = new TextBox { MaxLength = 120, Text = "Conversation " + (options.Count + 1) };
        var panel = new StackPanel { Margin = new Thickness(16) };
        panel.Children.Add(list);
        panel.Children.Add(Labeled("Conversation title (new or rename)", title));
        panel.Children.Add(Buttons(openChat, addChat, renameChat, cancel));
        var dialog = new Window { Owner = this, Title = "Conversations — " + w.Character.Name,
            Width = 440, SizeToContent = SizeToContent.Height, WindowStartupLocation = WindowStartupLocation.CenterOwner, ShowInTaskbar = false, Content = panel };
        Workspace.Conversation? selected = null;
        bool creating = false, renaming = false;
        renameChat.Click += (_, _) =>
        {
            selected = list.SelectedItem as Workspace.Conversation;
            if (selected == null) return;
            try { LocalHistory.ValidateText(title.Text); renaming = true; dialog.DialogResult = true; }
            catch (ArgumentException) { MessageBox.Show(dialog, "Enter a valid title.", "Mandy AI"); }
        };
        openChat.Click += (_, _) => { selected = list.SelectedItem as Workspace.Conversation; if (selected != null) dialog.DialogResult = true; };
        addChat.Click += (_, _) =>
        {
            try { LocalHistory.ValidateText(title.Text); creating = true; dialog.DialogResult = true; }
            catch (ArgumentException) { MessageBox.Show(dialog, "Enter a valid title.", "Mandy AI"); }
        };
        if (dialog.ShowDialog() != true) return;
        if (renaming && selected != null)
        {
            string renamed = title.Text, chatId = selected.Chat;
            busy = true; UpdateControls();
            try
            {
                await Task.Run(() => w.RenameConversation(chatId, renamed));
                charactersButton.ToolTip = w.ConversationTitle;
                status.Text = "Conversation renamed.";
            }
            catch (Exception) { status.Text = "Conversation rename failed."; }
            finally { Finish(); }
            return;
        }
        if (!creating && (selected == null || selected.Chat == w.Character.Chat)) return;
        var previous = w.Character;
        var previousPending = pending;
        var previousRows = history.ItemsSource;
        string previousDraft = draft.Text, previousSaved = lastSavedDraft, newTitle = title.Text;
        bool saveOld = pending == null && previousDraft != previousSaved;
        busy = true; UpdateControls();
        try
        {
            var loaded = await Task.Run(() =>
            {
                if (saveOld) w.SaveDraft(previousDraft);
                var target = creating ? w.AddConversation(newTitle) : selected!;
                w.SelectConversation(target.Character, target.Chat);
                var rows = w.Recent(); var savedDraft = w.ReadDraft();
                w.SaveSelection();
                return (Rows: rows, Draft: savedDraft);
            });
            draft.Text = loaded.Draft;
            lastSavedDraft = loaded.Draft;
            ShowRows(loaded.Rows);
            charactersButton.ToolTip = w.ConversationTitle;
            status.Text = pending == null ? "Conversation opened." : "Conversation opened · pending reply recovered.";
        }
        catch (Exception)
        {
            w.SelectConversation(previous.Character, previous.Chat);
            pending = previousPending;
            history.ItemsSource = previousRows;
            draft.Text = previousDraft;
            lastSavedDraft = previousSaved;
            charactersButton.ToolTip = w.ConversationTitle;
            historyValid = false;
            status.Text = "Conversation operation failed. Refresh the original conversation before continuing.";
        }
        finally { Finish(); }
    }

    private async Task SaveCurrentDraftAsync()
    {
        var w = workspace;
        if (busy || w == null || !historyValid || pending != null || closeRequested) return;
        string text = draft.Text;
        busy = true; UpdateControls();
        try
        {
            await Task.Run(() => w.SaveDraft(text));
            lastSavedDraft = text;
            status.Text = "Draft saved locally.";
        }
        catch (Exception) { status.Text = "Draft could not be saved. Text remains in the composer."; }
        finally { Finish(); }
    }

    private async Task RefreshAsync()
    {
        var w = workspace;
        if (busy || w == null) return;
        busy = true; UpdateControls();
        try
        {
            ShowRows(await Task.Run(w.Recent));
            status.Text = pending == null ? "History refreshed." : "Pending message recovered.";
        }
        catch (Exception) { historyValid = false; status.Text = "History unavailable. Sending disabled until refresh succeeds."; }
        finally { Finish(); }
    }
    private void PickSpeechFile(bool executable)
    {
        if (busy) return;
        var picker = new OpenFileDialog
        {
            Filter = executable ? "Whisper CLI (whisper-cli.exe)|whisper-cli.exe" : "Whisper GGML model (*.bin)|*.bin",
            CheckFileExists = true,
            Title = executable ? "Choose trusted local whisper.cpp executable" : "Choose trusted multilingual speech model"
        };
        if (picker.ShowDialog(this) != true) return;
        if (executable)
        {
            if (!Path.GetFileName(picker.FileName).Equals("whisper-cli.exe", StringComparison.OrdinalIgnoreCase)) return;
            if (MessageBox.Show(this, "This executable will run with your Windows permissions. Only select a verified trusted whisper.cpp release. Use this executable for local dictation?", "Mandy AI", MessageBoxButton.OKCancel, MessageBoxImage.Warning) != MessageBoxResult.OK) return;
            whisperExe.Text = picker.FileName;
        }
        else whisperModel.Text = picker.FileName;
    }

    private void StartDictation()
    {
        if (busy || workspace == null || !historyValid || pending != null || closeRequested) return;
        if (!File.Exists(whisperExe.Text) || !File.Exists(whisperModel.Text))
        {
            configuration.Visibility = Visibility.Visible;
            status.Text = "Choose a trusted whisper-cli.exe and multilingual GGML model in Settings.";
            return;
        }
        var capture = new MicrophoneRecorder();
        try
        {
            recordingDraft = draft.Text;
            recordingStart = draft.SelectionStart;
            recordingLength = draft.SelectionLength;
            speechExePath = whisperExe.Text;
            speechModelPath = whisperModel.Text;
            recordingLanguage = speechLanguage.SelectedItem as string ?? "auto";
            capture.Start();
            recorder = capture;
            busy = true;
            operation = new CancellationTokenSource();
            speechFinishing = false;
            configuration.Visibility = Visibility.Collapsed;
            UpdateControls();
            status.Text = "Recording locally · maximum 3 minutes";
        }
        catch (Exception)
        {
            try { capture.Dispose(); }
            catch (Exception) { /* Preserve the original start failure and restore the UI. */ }
            finally { MicrophoneRecorder.EraseTemporaryRecording(capture.FilePath); }
            recorder = null;
            Finish();
            status.Text = "Microphone could not start. Draft preserved.";
        }
    }

    private async Task EndDictationAsync(bool sendAfter, bool discard)
    {
        var capture = recorder;
        if (capture == null || speechFinishing) return;
        speechFinishing = true;
        var token = operation?.Token ?? CancellationToken.None;
        UpdateControls();
        string? wav = null;
        bool sendRequested = false;
        bool transcriptInserted = false;
        try
        {
            if (discard)
            {
                await capture.CancelAsync();
                status.Text = "Recording cancelled. Draft preserved.";
            }
            else
            {
                status.Text = "Transcribing locally…";
                wav = await capture.StopAsync();
                token.ThrowIfCancellationRequested();
                var transcript = await WhisperCliTranscriber.TranscribeAsync(speechExePath, speechModelPath, wav, recordingLanguage, token);
                token.ThrowIfCancellationRequested();
                if (closeRequested || draft.Text != recordingDraft || workspace == null || pending != null) return;
                var before = recordingDraft[..recordingStart];
                var after = recordingDraft[(recordingStart + recordingLength)..];
                string insert = (before.Length > 0 && !char.IsWhiteSpace(before[^1]) ? " " : "") +
                    transcript + (after.Length > 0 && !char.IsWhiteSpace(after[0]) ? " " : "");
                string next = before + insert + after;
                if (next.Length > draft.MaxLength || string.IsNullOrWhiteSpace(next) ||
                    next.Any(c => char.IsControl(c) && c != '\r' && c != '\n' && c != '\t'))
                { status.Text = "Transcript rejected: size or invalid text. Draft preserved."; return; }
                draft.Text = next;
                transcriptInserted = true;
                draft.Select(before.Length + insert.Length, 0);
                // Keep is an explicit save request. Persist before considering Send.
                await Task.Run(() => workspace!.SaveDraft(next));
                lastSavedDraft = next;
                token.ThrowIfCancellationRequested();
                sendRequested = sendAfter;
                status.Text = "Dictation saved and ready to review.";
            }
        }
        catch (OperationCanceledException) { status.Text = transcriptInserted ? "Send cancelled. Transcript retained in draft." : "Dictation cancelled. Draft preserved."; }
        catch (Exception) { status.Text = transcriptInserted ? "Transcript retained in composer; draft save failed." : "Local transcription failed. Draft preserved."; }
        finally
        {
            try { capture.Dispose(); }
            catch (Exception) { status.Text = "Microphone cleanup failed; check the recording device."; sendRequested = false; }
            finally { MicrophoneRecorder.EraseTemporaryRecording(wav ?? capture.FilePath); }
            recorder = null;
            speechFinishing = false;
            recordingDraft = speechExePath = speechModelPath = "";
            Finish();
        }
        if (sendRequested && !closeRequested && !token.IsCancellationRequested)
            await SendAsync("dictation");
    }

    private async Task SendAsync(string mode = "text")
    {
        var w = workspace;
        if (busy || !historyValid || w == null) return;
        if (!int.TryParse(port.Text, out int localPort)) { status.Text = "Invalid local port."; return; }
        LocalInferenceClient client;
        try { client = new LocalInferenceClient(alias.Text, localPort, key.Password); }
        catch (Exception) { status.Text = "Invalid local server settings or key."; return; }
        // Do not persist the server credential or print provider exception messages.
        var p = w.Character;
        var text = pending?.Text ?? draft.Text;
        if (string.IsNullOrWhiteSpace(text)) { status.Text = "Enter a message."; return; }
        var request = new LocalChatTurn.Request(p.Account,p.Device,p.Character,p.Chat,
            pending?.Id ?? Guid.NewGuid().ToString(),text,p.Identity,pending?.Mode ?? mode);
        var turn = new LocalChatTurn(w.Database, client);
        busy = true; operation = new CancellationTokenSource(); UpdateControls();
        status.Text = "Generating locally…";
        liveReply.Clear();
        liveReply.Visibility = Visibility.Collapsed;
        // The provider bounds output to 16,000 UTF-16 units. Coalesce fragments on
        // the calling worker, with at most one synchronous UI dispatch outstanding.
        // No timer, detached task or polling is needed.
        var preview = new StringBuilder();
        var cadence = Stopwatch.StartNew();
        bool first = true;
        var token = operation.Token;
        void OnFragment(string fragment)
        {
            token.ThrowIfCancellationRequested();
            preview.Append(fragment);
            if (!first && cadence.ElapsedMilliseconds < 50) return;
            first = false;
            string snapshot = preview.ToString();
            Dispatcher.Invoke(() =>
            {
                if (token.IsCancellationRequested || closeRequested) return;
                liveReply.Visibility = Visibility.Visible;
                liveReply.Text = snapshot;
                liveReply.ScrollToEnd();
            }, DispatcherPriority.Background);
            cadence.Restart();
        }
        bool saved = false;
        try
        {
            if (pending == null)
            {
                await Task.Run(() => w.SaveDraft(text));
                lastSavedDraft = text;
            }
            await turn.SendAsync(request, preview: OnFragment, cancellationToken: token);
            saved = true;
            draft.Clear();
            status.Text = "Reply saved.";
        }
        catch (OperationCanceledException) { status.Text = "Stopped. Any saved user message can be retried."; }
        catch (Exception) { status.Text = "Local turn failed. Check the trusted local server and retry."; }
        finally
        {
            try
            {
                ShowRows(await Task.Run(w.Recent));
                lastSavedDraft = await Task.Run(w.ReadDraft);
                if (!saved && pending == null) draft.Text = text;
            }
            catch (Exception)
            {
                historyValid = false;
                status.Text = "History unavailable. Refresh before sending again.";
            }
            // Only durable database rows are displayed as completed messages.
            liveReply.Clear();
            liveReply.Visibility = Visibility.Collapsed;
            Finish();
        }
    }
}
