package io.github.radixtrator.resync.ui.common

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's icon choices, all from the vendored Material Symbols Rounded (see Symbols), named for what they mean here so each screen picks by role.
 */
object AppIcons {
    val Comment: ImageVector get() = Symbols.ChatBubble
    val Bookmark: ImageVector get() = Symbols.Bookmark
    val BookmarkFilled: ImageVector get() = Symbols.BookmarkFilled
    val Gallery: ImageVector get() = Symbols.PhotoLibrary
    val OpenExternal: ImageVector get() = Symbols.OpenInNew
    val Reply: ImageVector get() = Symbols.Reply
    val Sort: ImageVector get() = Symbols.Sort

    /** Sort sheet glyphs: Hot, New, Rising, Top, Controversial. */
    val Flame: ImageVector get() = Symbols.Whatshot
    val Burst: ImageVector get() = Symbols.NewReleases
    val TrendingUp: ImageVector get() = Symbols.TrendingUp
    val TrendingDown: ImageVector get() = Symbols.TrendingDown
    val Bars: ImageVector get() = Symbols.BarChart
    val Compass: ImageVector get() = Symbols.Explore
    val Moon: ImageVector get() = Symbols.DarkMode

    /** Sync's "Change view". */
    val ViewStack: ImageVector get() = Symbols.ViewAgenda
    val DoubleCheck: ImageVector get() = Symbols.DoneAll

    /** Sync's solid thumbtack, after pinned titles and in pinned comments' headers. */
    val Pin: ImageVector get() = Symbols.PushPinFilled

    /** Content-type marks for list rows: image, video, link, text. */
    val Image: ImageVector get() = Symbols.Image
    val PlayCircle: ImageVector get() = Symbols.PlayCircle
    val Link: ImageVector get() = Symbols.Link
    val Text: ImageVector get() = Symbols.Notes

    /** Straight vote arrows, as on Sync's comment action bar (chevrons there mean "go to parent"). */
    val ArrowUp: ImageVector get() = Symbols.ArrowUpward
    val ArrowDown: ImageVector get() = Symbols.ArrowDownward

    /** Moderator mark and mod tools. */
    val Shield: ImageVector get() = Symbols.Shield

    /** Sync's OP mark: a person speaking. */
    val Speaker: ImageVector get() = Symbols.RecordVoiceOverFilled
    val Clock: ImageVector get() = Symbols.Schedule
    val CollapseAll: ImageVector get() = Symbols.UnfoldLess
    val Copy: ImageVector get() = Symbols.ContentCopy
    val RemoveCircle: ImageVector get() = Symbols.DoNotDisturbOn
    val PersonAdd: ImageVector get() = Symbols.PersonAdd

    /** Comment and post options: Filter, Report, Tag user. */
    val Filter: ImageVector get() = Symbols.FilterList
    val Report: ImageVector get() = Symbols.Report
    val TagUser: ImageVector get() = Symbols.Badge

    /** Profile chips: Trophies, Block. */
    val Trophy: ImageVector get() = Symbols.EmojiEvents
    val Block: ImageVector get() = Symbols.Block

    /** Community header chips: Join, Wiki, Flairs, Random. */
    val PlaylistAdd: ImageVector get() = Symbols.PlaylistAdd
    val Book: ImageVector get() = Symbols.Book
    val Tag: ImageVector get() = Symbols.Label
    val Dice: ImageVector get() = Symbols.Casino

    /** Media viewer controls (Sync's video bar and info sheet). */
    val Pause: ImageVector get() = Symbols.PauseFilled
    val Restart: ImageVector get() = Symbols.SkipPreviousFilled
    val VolumeOn: ImageVector get() = Symbols.VolumeUpFilled
    val VolumeOff: ImageVector get() = Symbols.VolumeOffFilled

    /** Sync's "slower" speed button. */
    val RemoveCircleFilled: ImageVector get() = Symbols.DoNotDisturbOnFilled
    val Download: ImageVector get() = Symbols.Download

    /** Settings categories. */
    val Tablet: ImageVector get() = Symbols.TabletAndroid
    val Palette: ImageVector get() = Symbols.Palette
    val DataTransfer: ImageVector get() = Symbols.DataUsage
    val Backup: ImageVector get() = Symbols.Backup

    /** Quick actions: Dark overlay, Data saving. */
    val Contrast: ImageVector get() = Symbols.Contrast
    val DataSaver: ImageVector get() = Symbols.DataSaverOn
}
