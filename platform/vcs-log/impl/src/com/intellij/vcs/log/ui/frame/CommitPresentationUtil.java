// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.FontUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer.formatTextWithLinks;
import static com.intellij.openapi.vcs.ui.FontUtil.getCommitMessageFont;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class CommitPresentationUtil {
  @NotNull private static final Pattern HASH_PATTERN = Pattern.compile("[0-9a-f]{7,40}", Pattern.CASE_INSENSITIVE);

  @NotNull static final String GO_TO_HASH = "go-to-hash:";
  @NotNull static final String SHOW_HIDE_BRANCHES = "show-hide-branches";

  @NotNull
  private static String escapeMultipleSpaces(@NotNull String text) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == ' ') {
        if (i == text.length() - 1 || text.charAt(i + 1) != ' ') {
          result.append(' ');
        }
        else {
          result.append("&nbsp;");
        }
      }
      else {
        result.append(text.charAt(i));
      }
    }
    return result.toString();
  }

  @NotNull
  private static Set<String> findHashes(@NotNull String text) {
    Set<String> result = ContainerUtil.newHashSet();
    Matcher matcher = HASH_PATTERN.matcher(text);
    while (matcher.find()) {
      result.add(matcher.group());
    }
    return result;
  }

  @NotNull
  private static String replaceHashes(@NotNull String s, @NotNull Set<String> resolvedHashes) {
    Matcher matcher = HASH_PATTERN.matcher(s);
    StringBuffer result = new StringBuffer();

    while (matcher.find()) {
      String hash = matcher.group();

      if (resolvedHashes.contains(hash)) {
        hash = "<a href=\"" + GO_TO_HASH + hash + "\">" + hash + "</a>";
      }
      matcher.appendReplacement(result, hash);
    }
    matcher.appendTail(result);

    return result.toString();
  }

  @NotNull
  private static Set<String> findHashes(@NotNull Project project,
                                        @NotNull String subject,
                                        @NotNull String description) {
    Set<String> unresolvedHashes = ContainerUtil.newHashSet();
    Convertor<String, String> convertor = s -> {
      unresolvedHashes.addAll(findHashes(s));
      return s;
    };
    formatTextWithLinks(project, subject, convertor);
    formatTextWithLinks(project, description, convertor);
    return unresolvedHashes;
  }

  @NotNull
  private static String formatCommitText(@NotNull Project project,
                                         @NotNull String subject,
                                         @NotNull String description,
                                         @NotNull Set<String> resolvedHashes) {
    Convertor<String, String> convertor = s -> replaceHashes(s, resolvedHashes);
    Font font = getCommitMessageFont();
    return "<b>" +
           FontUtil.getHtmlWithFonts(escapeMultipleSpaces(formatTextWithLinks(project, subject, convertor)), Font.BOLD, font) +
           "</b>" +
           FontUtil.getHtmlWithFonts(escapeMultipleSpaces(formatTextWithLinks(project, description, convertor)), font.getStyle(), font);
  }

  @NotNull
  private static String getAuthorText(@NotNull VcsFullCommitDetails commit) {
    long authorTime = commit.getAuthorTime();
    long commitTime = commit.getCommitTime();

    String authorText = getAuthorName(commit.getAuthor()) + formatDateTime(authorTime);
    if (!VcsUserUtil.isSamePerson(commit.getAuthor(), commit.getCommitter())) {
      String commitTimeText;
      if (authorTime != commitTime) {
        commitTimeText = formatDateTime(commitTime);
      }
      else {
        commitTimeText = "";
      }
      authorText += "<br/>" + getCommitterText(commit.getCommitter(), commitTimeText);
    }
    else if (authorTime != commitTime) {
      authorText += "<br/>" + getCommitterText(null, formatDateTime(commitTime));
    }
    return authorText;
  }

  @NotNull
  private static String getCommitterText(@Nullable VcsUser committer, @NotNull String commitTimeText) {
    String graySpan = "<span style='color:#" + ColorUtil.toHex(JBColor.GRAY) + "'>";
    String text = graySpan + "committed";
    if (committer != null) {
      text += " by " + VcsUserUtil.getShortPresentation(committer);
      if (!committer.getEmail().isEmpty()) {
        text += "</span>" + getEmailText(committer) + graySpan;
      }
    }
    text += commitTimeText + "</span>";
    return text;
  }

  @NotNull
  private static String getAuthorName(@NotNull VcsUser user) {
    String username = VcsUserUtil.getShortPresentation(user);
    return user.getEmail().isEmpty() ? username : username + getEmailText(user);
  }

  @NotNull
  private static String getEmailText(@NotNull VcsUser user) {
    return " <a href='mailto:" + user.getEmail() + "'>&lt;" + user.getEmail() + "&gt;</a>";
  }

  @NotNull
  public static String formatDateTime(long time) {
    return " on " + DateFormatUtil.formatDate(time) + " at " + DateFormatUtil.formatTime(time);
  }

  @NotNull
  static String getBranchesText(@Nullable List<String> branches, boolean expanded, int availableWidth, @NotNull FontMetrics metrics) {
    if (branches == null) {
      return "In branches: loading...";
    }
    if (branches.isEmpty()) return "Not in any branch";

    String head = "In " + branches.size() + StringUtil.pluralize(" branch", branches.size()) + ": ";

    if (expanded) {
      return head +
             "<a href=\"" + SHOW_HIDE_BRANCHES + "\">Hide</a><br/>" +
             StringUtil.join(branches, "<br/>");
    }

    String tail = "… <a href=\"" + SHOW_HIDE_BRANCHES + "\">Show all</a>";
    int headWidth = metrics.stringWidth(head);
    int tailWidth = metrics.stringWidth(StringUtil.removeHtmlTags(tail));
    if (availableWidth <= headWidth + tailWidth) {
      return head + tail; // oh well
    }

    availableWidth -= headWidth;
    StringBuilder branchesText = new StringBuilder();
    for (int i = 0; i < branches.size(); i++) {
      String branch = branches.get(i) + (i != branches.size() - 1 ? ", " : "");
      int branchWidth = metrics.stringWidth(branch);
      if (branchWidth + tailWidth < availableWidth) {
        branchesText.append(branch);
        availableWidth -= branchWidth;
      }
      else {
        StringBuilder shortenedBranch = new StringBuilder();
        for (char c : branch.toCharArray()) {
          if (metrics.stringWidth(shortenedBranch.toString() + c) + tailWidth >= availableWidth) {
            break;
          }
          shortenedBranch.append(c);
        }
        branchesText.append(shortenedBranch);
        branchesText.append(tail);
        break;
      }
    }

    return head + branchesText.toString();
  }

  @NotNull
  public static CommitPresentation buildPresentation(@NotNull Project project,
                                                     @NotNull VcsFullCommitDetails commit,
                                                     @NotNull Set<String> unresolvedHashes) {
    Font font = FontUtil.getCommitMetadataFont();
    String hashAndAuthor = FontUtil.getHtmlWithFonts(commit.getId().toShortString() + " " + getAuthorText(commit), font.getStyle(), font);
    String fullMessage = commit.getFullMessage();
    int separator = fullMessage.indexOf("\n\n");
    String subject = separator > 0 ? fullMessage.substring(0, separator) : fullMessage;
    String description = fullMessage.substring(subject.length());

    Set<String> unresolvedHashesForCommit = findHashes(project, subject, description);
    String text = formatCommitText(project, subject, description, Collections.emptySet());
    if (unresolvedHashesForCommit.isEmpty()) {
      return new CommitPresentation(text, hashAndAuthor, commit.getRoot(), MultiMap.empty());
    }

    unresolvedHashes.addAll(unresolvedHashesForCommit);
    return new UnresolvedPresentation(project, commit.getRoot(), subject, description, hashAndAuthor, text);
  }

  private static class UnresolvedPresentation extends CommitPresentation {
    private final Project myProject;
    private final String mySubject;
    private final String myDescription;

    public UnresolvedPresentation(@NotNull Project project,
                                  @NotNull VirtualFile root,
                                  @NotNull String subject,
                                  @NotNull String description,
                                  @NotNull String hashAndAuthor,
                                  @NotNull String text) {
      super(text, hashAndAuthor, root, MultiMap.empty());
      myProject = project;
      mySubject = subject;
      myDescription = description;
    }

    @NotNull
    public CommitPresentation resolve(@NotNull MultiMap<String, CommitId> resolvedHashes) {
      String text = formatCommitText(myProject, mySubject, myDescription, resolvedHashes.keySet());
      return new CommitPresentation(text, myHashAndAuthor, myRoot, resolvedHashes);
    }

    @Override
    public boolean isResolved() {
      return false;
    }
  }

  public static class CommitPresentation {
    @NotNull protected final String myText;
    @NotNull protected final String myHashAndAuthor;
    @NotNull protected final VirtualFile myRoot;
    @NotNull private final MultiMap<String, CommitId> myResolvedHashes;

    public CommitPresentation(@NotNull String text, @NotNull String hashAndAuthor,
                              @NotNull VirtualFile root, @NotNull MultiMap<String, CommitId> resolvedHashes) {
      myText = text;
      myHashAndAuthor = hashAndAuthor;
      myRoot = root;
      myResolvedHashes = resolvedHashes;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    @NotNull
    public String getHashAndAuthor() {
      return myHashAndAuthor;
    }

    @Nullable
    public CommitId parseTargetCommit(@NotNull HyperlinkEvent e) {
      if (!e.getDescription().startsWith(GO_TO_HASH)) return null;
      String hash = e.getDescription().substring(GO_TO_HASH.length());
      Collection<CommitId> ids = myResolvedHashes.get(hash);
      if (ids.size() <= 1) return getFirstItem(ids);
      for (CommitId id : ids) {
        if (myRoot.equals(id.getRoot())) {
          return id;
        }
      }
      return getFirstItem(ids);
    }

    @NotNull
    public CommitPresentation resolve(@NotNull MultiMap<String, CommitId> resolvedHashes) {
      return this;
    }

    public boolean isResolved() {
      return true;
    }
  }
}
