using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Media;
using System.Windows.Shapes;

namespace Bwdacc.Desktop;

internal static class DesktopVisuals
{
    internal static void Navigation(Button button, string title, string geometry)
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal };
        var icon = new Path { Data = Geometry.Parse(geometry), Width = 20, Height = 20, Stretch = Stretch.Uniform, StrokeThickness = 1.6, StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round, Margin = new(0, 0, 12, 0) };
        icon.SetBinding(Shape.StrokeProperty, new Binding("Foreground") { Source = button });
        row.Children.Add(icon); row.Children.Add(new TextBlock { Text = title, FontFamily = (FontFamily)Application.Current.FindResource("TitleFont"), FontSize = 13, VerticalAlignment = VerticalAlignment.Center });
        button.Content = row; button.HorizontalContentAlignment = HorizontalAlignment.Left; button.BorderThickness = new(0); button.Padding = new(15, 12, 12, 12);
        System.Windows.Automation.AutomationProperties.SetName(button, title);
    }
    internal static Border Step(string number, string title, string detail)
    {
        var grid = new Grid(); grid.ColumnDefinitions.Add(new() { Width = new(42) }); grid.ColumnDefinitions.Add(new());
        var marker = new Border { Background = new SolidColorBrush(Color.FromRgb(43, 44, 48)), CornerRadius = new(16), Width = 34, Height = 34, VerticalAlignment = VerticalAlignment.Top, Child = new TextBlock { Text = number, Foreground = Brushes.White, FontFamily = (FontFamily)Application.Current.FindResource("TitleFont"), HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center } };
        grid.Children.Add(marker); var words = new StackPanel(); Grid.SetColumn(words, 1);
        words.Children.Add(new TextBlock { Text = title, FontFamily = (FontFamily)Application.Current.FindResource("TitleFont"), FontSize = 14, Foreground = Brushes.White, Margin = new(0, 0, 0, 5) });
        words.Children.Add(new TextBlock { Text = detail, FontSize = 12, Foreground = (Brush)Application.Current.FindResource("Muted") });grid.Children.Add(words);
        return new Border { Child = grid, Margin = new(0, 8, 0, 12) };
    }
}
