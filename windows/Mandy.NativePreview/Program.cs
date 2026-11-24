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
            app.Resources.MergedDictionaries.Add(new ResourceDictionary
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
    private readonly TextBox liveReply = new() { IsReadOnly = true, TextWrapping = TextWrapping.Wrap, MaxHeight = 160, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Visibility = Visibility.Collapsed, IsUndoEnabled = false, Margin = new Thickness(0, 0, 0, 8) };
    private readonly TextBlock status = new() { Text = "Choose an empty folder to create a workspace, or open an existing one.", TextWrapping = TextWrapping.Wrap };
    private readonly Button choose = new() { Content = "Choose folder" };
    private readonly Button create = new() { Content = "Create" };
    private readonly Button open = new() { Content = "Open" };
    private readonly Button send = new() { Content = "Send", IsEnabled = false };
    private readonly Button stop = new() { Content = "Stop", IsEnabled = false };
    private readonly Button refresh = new() { Content = "Refresh", IsEnabled = false };
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
        var headerActions = Buttons(Unavailable("Call", "Live voice calls are not implemented in this preview."), settingsButton);
        DockPanel.SetDock(headerActions, Dock.Right);
        header.Children.Add(headerActions);
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
        settings.Children.Add(Buttons(refresh));
        var composer = new StackPanel { Margin = new Thickness(0,8,0,0) };
        DockPanel.SetDock(composer, Dock.Bottom);
        layout.Children.Add(composer);
        composer.Children.Add(liveReply);
        draft.Padding = new Thickness(12,8,12,8);
        draft.FontSize = 16;
        composer.Children.Add(draft);
        var composerActions = new DockPanel { LastChildFill = false };
        var primaryActions = Buttons(send, stop);
        DockPanel.SetDock(primaryActions, Dock.Right);
        composerActions.Children.Add(primaryActions);
        var mediaActions = Buttons(Unavailable("Attach", "Multimodal attachments are not implemented in this preview."),
            Unavailable("Dictate", "Native speech recognition is not connected to this workspace yet."));
        DockPanel.SetDock(mediaActions, Dock.Left);
        composerActions.Children.Add(mediaActions);
        composer.Children.Add(composerActions);
        composer.Children.Add(status);
        history.Background = Brushes.Transparent;
        history.BorderThickness = new Thickness(0);
        history.FontSize = 16;
        VirtualizingPanel.SetIsVirtualizing(history, true);
        VirtualizingPanel.SetVirtualizationMode(history, VirtualizationMode.Recycling);
        ScrollViewer.SetCanContentScroll(history, true);
        ScrollViewer.SetHorizontalScrollBarVisibility(history, ScrollBarVisibility.Disabled);
        // Preserve virtualization: bounded data, native text layout, no embedded browser.
        var template = new DataTemplate(typeof(MessageRow));
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
        template.VisualTree = stack;
        history.ItemTemplate = template;
        var items = new Style(typeof(ListBoxItem));
        items.Setters.Add(new Setter(Control.HorizontalContentAlignmentProperty, HorizontalAlignment.Stretch));
        var user = new DataTrigger { Binding = new System.Windows.Data.Binding(nameof(MessageRow.IsUser)), Value = true };
        user.Setters.Add(new Setter(Control.HorizontalContentAlignmentProperty, HorizontalAlignment.Right));
        user.Setters.Add(new Setter(FrameworkElement.MarginProperty, new Thickness(56,0,0,0)));
        items.Triggers.Add(user);
        history.ItemContainerStyle = items;
        layout.Children.Add(history);
        Content = layout;
        AutomationProperties.SetName(draft, "Message draft");
        AutomationProperties.SetName(history, "Conversation history");
        AutomationProperties.SetName(liveReply, "Incomplete streamed reply");
        AutomationProperties.SetName(key, "Local server API key");
        AutomationProperties.SetName(settingsButton, "Show or hide settings");
        draft.PreviewKeyDown += async (_, e) =>
        {
            if (e.Key == Key.Enter && Keyboard.Modifiers == ModifierKeys.Control && send.IsEnabled)
            {
                e.Handled = true;
                await SendAsync();
            }
        };
        PreviewKeyDown += (_, e) =>
        {
            if (e.Key == Key.Escape && stop.IsEnabled)
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
                status.Text = "Finishing the active operation before closing…";
                return;
            }
            workspace?.Dispose();
        };
        UpdateControls();
    }

    private static Button Unavailable(string label, string reason)
    {
        var button = new Button { Content = label, IsEnabled = false, ToolTip = reason };
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
    private static FrameworkElement Buttons(params Button[] buttons)
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
        send.IsEnabled = ready;
        send.Content = pending != null ? "Retry pending message" : "Send";
        refresh.IsEnabled = workspace != null && !busy;
        stop.IsEnabled = busy && operation != null && !operation.IsCancellationRequested;
        stop.Visibility = busy && operation != null ? Visibility.Visible : Visibility.Collapsed;
        send.Visibility = busy && operation != null ? Visibility.Collapsed : Visibility.Visible;
    }
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
        if (pending != null) draft.Text = pending.Text;
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
            ShowRows(await Task.Run(workspace.Recent));
            configuration.Visibility = Visibility.Collapsed;
            characterTitle.Text = workspace.Character.Name;
            status.Text = pending == null ? "Local workspace ready." : "A saved user message is awaiting a reply.";
        }
        catch (Exception)
        {
            workspace?.Dispose(); workspace = null; historyValid = false;
            status.Text = "Workspace open failed. Existing files were not reset.";
        }
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
    private async Task SendAsync()
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
            pending?.Id ?? Guid.NewGuid().ToString(),text,p.Identity,pending?.Mode ?? "text");
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
