using System.IO;
using System.Diagnostics;
using System.Text;
using System.Windows.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Microsoft.Win32;
using CharacterCompanion.Windows.Intelligence;

namespace Mandy.NativePreview;

internal static class Program
{
    [STAThread]
    public static int Main()
    {
        var app = new Application { ShutdownMode = ShutdownMode.OnMainWindowClose };
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
    private readonly Expander configuration = new() { Header = "Workspace and local AI settings", IsExpanded = true };
    private readonly TextBox liveReply = new() { IsReadOnly = true, TextWrapping = TextWrapping.Wrap, MaxHeight = 160, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Visibility = Visibility.Collapsed, IsUndoEnabled = false, Margin = new Thickness(0, 0, 0, 8) };
    private readonly TextBlock status = new() { Text = "Choose an empty folder to create a workspace, or open an existing one.", TextWrapping = TextWrapping.Wrap };
    private readonly Button choose = new() { Content = "Choose folder" };
    private readonly Button create = new() { Content = "Create" };
    private readonly Button open = new() { Content = "Open" };
    private readonly Button send = new() { Content = "Send", IsEnabled = false };
    private readonly Button stop = new() { Content = "Stop", IsEnabled = false };
    private readonly Button refresh = new() { Content = "Refresh", IsEnabled = false };
    private Workspace? workspace;
    private LocalHistory.Event? pending;
    private CancellationTokenSource? operation;
    private bool busy, closeRequested, historyValid;

    internal ChatWindow()
    {
        Title = "Mandy AI — Native Windows preview";
        Width = 920; Height = 800; MinWidth = 640; MinHeight = 590;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        Background = new SolidColorBrush(Color.FromRgb(17, 19, 23));
        Foreground = Brushes.WhiteSmoke;
        var layout = new DockPanel { Margin = new Thickness(18), LastChildFill = true };
        var settings = new StackPanel();
        DockPanel.SetDock(configuration, Dock.Top);
        configuration.Content = settings;
        layout.Children.Add(configuration);
        settings.Children.Add(new TextBlock { Text = "Mandy AI", FontSize = 24, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0,0,0,10) });
        settings.Children.Add(Labeled("Local workspace", folder));
        settings.Children.Add(Buttons(choose, create, open));
        settings.Children.Add(Labeled("Character name (creation)", name));
        settings.Children.Add(Labeled("Character identity (creation)", identity));
        settings.Children.Add(Labeled("Trusted local server model alias", alias));
        settings.Children.Add(Labeled("Loopback port", port));
        settings.Children.Add(Labeled("Local server API key (not saved)", key));
        var composer = new StackPanel { Margin = new Thickness(0,8,0,0) };
        DockPanel.SetDock(composer, Dock.Bottom);
        layout.Children.Add(composer);
        composer.Children.Add(liveReply);
        composer.Children.Add(draft);
        composer.Children.Add(Buttons(send, stop, refresh));
        composer.Children.Add(status);
        history.Background = new SolidColorBrush(Color.FromRgb(29,32,38));
        history.Foreground = Brushes.WhiteSmoke;
        VirtualizingPanel.SetIsVirtualizing(history, true);
        VirtualizingPanel.SetVirtualizationMode(history, VirtualizationMode.Recycling);
        ScrollViewer.SetCanContentScroll(history, true);
        ScrollViewer.SetHorizontalScrollBarVisibility(history, ScrollBarVisibility.Disabled);
        // Only the latest 50 messages are loaded. A wrapping text template avoids wide rows.
        var template = new DataTemplate();
        var text = new FrameworkElementFactory(typeof(TextBlock));
        text.SetBinding(TextBlock.TextProperty, new System.Windows.Data.Binding());
        text.SetValue(TextBlock.TextWrappingProperty, TextWrapping.Wrap);
        text.SetValue(TextBlock.MarginProperty, new Thickness(8));
        template.VisualTree = text;
        history.ItemTemplate = template;
        layout.Children.Add(history);
        Content = layout;
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
            (e.Role == "user" ? "You" : w.Character.Name) + "\n" + e.Text).ToArray();
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
            configuration.IsExpanded = false;
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
