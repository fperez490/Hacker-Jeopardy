import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Comparator;

public class JeopardyGUI {
    private JFrame frame;
    private JPanel boardPanel, scorePanel;
    private final Map<String, List<Clue>> byCategory = new LinkedHashMap<>();
    private final List<String> categoryOrder = new ArrayList<>();
    private final List<Clue>  allClues= new ArrayList<>();
    private final Contestant[] contestants = new Contestant[3];
    private Clue finalJeopardyClue = null;
    private final Random rand = new Random();

    // Colors and fonts
    private final Color darkBlue = new Color(0, 0, 128);
    private final Color gold = new Color(255, 215, 0);
    private final Color highlightBlue = new Color(30, 30, 60);

    private Font getGameFont(int size, boolean bold) {
        int style = bold ? Font.BOLD : Font.PLAIN;
        Font f = new Font("Arial Black", style, size);
        if (!f.getFamily().equalsIgnoreCase("Arial Black")) {
            f = new Font("Serif", style, size);
        }
        return f;
    }
    private Font getScaledFont(double scaleFactor, int minSize) {
        int size = Math.max((int)(frame.getHeight() * scaleFactor), minSize);
        return getGameFont(size, true);
    }

    private JButton createGameButton(String text, int fontSize, Color bgColor, Color fgColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();

                // Use provided color or default dark blue base
                Color base = (bgColor != null) ? bgColor : new Color(0, 0, 100);
                Color top = base.brighter();
                Color bottom = base.darker();

                // Gradient fill
                GradientPaint gp = new GradientPaint(0, 0, top, 0, h, bottom);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, w, h, 25, 25);

                // Soft border
                g2.setColor(new Color(255, 255, 255, 60));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 25, 25);

                super.paintComponent(g2);
                g2.dispose();
            }
        };

        btn.setFont(new Font("Arial Black", Font.BOLD, fontSize));
        btn.setForeground(fgColor != null ? fgColor : Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Subtle hover brighten effect
        Color baseBg = (bgColor != null) ? bgColor : new Color(0, 0, 100);
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(baseBg.brighter());
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(baseBg);
            }
        });

        return btn;
    }


    public void start() {
        // Unified dialog theme
        UIManager.put("OptionPane.background", darkBlue);
        UIManager.put("Panel.background", darkBlue);
        UIManager.put("OptionPane.messageForeground", Color.WHITE);

        frame = new JFrame("Jeopardy");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 750);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(10, 10));
        frame.getContentPane().setBackground(darkBlue);

        createScorePanel();
        createBoardPlaceholder();
        loadCluesDialog();
        getContestantNames();
        rebuildBoard();
        enableDynamicFontScaling(boardPanel);

        frame.setVisible(true);
    }

    private void createScorePanel() {
        scorePanel = new JPanel();
        scorePanel.setLayout(new BoxLayout(scorePanel, BoxLayout.Y_AXIS));
        scorePanel.setPreferredSize(new Dimension(240, 0));
        scorePanel.setBackground(darkBlue);
        scorePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.WHITE),
                "Scores", 0, 0, new Font("SansSerif", Font.BOLD, 18), Color.WHITE));

        // Top buttons
        JButton fjBtn = createGameButton("Final Jeopardy", 18, null, null);
        fjBtn.addActionListener(_ -> {
            int confirm = JOptionPane.showConfirmDialog(frame, "Move to Final Jeopardy?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) showFinalJeopardyCategorySlide();
        });

        JButton adjustBtn = createGameButton("Adjust Score", 18, null, null);
        adjustBtn.addActionListener(_ -> adjustScoreDialog());

        JButton newGameBtn = createGameButton("New Game", 18, null, null);
        newGameBtn.addActionListener(_ -> {
            int confirm = JOptionPane.showConfirmDialog(frame,
                    "Start a new game? All scores and board progress will be reset.",
                    "Confirm Reset", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                loadCluesDialog();
                rebuildBoard();
                for (Contestant c : contestants) c.score = 0;
                updateScoreLabels();
            }
        });

        JPanel controlGroup = new JPanel();
        controlGroup.setLayout(new GridLayout(3, 1, 6, 6));
        controlGroup.setBackground(darkBlue);
        controlGroup.add(fjBtn);
        controlGroup.add(adjustBtn);
        controlGroup.add(newGameBtn);
        controlGroup.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.WHITE, 1),
                "Game Controls", 0, 0, getGameFont(16, true), Color.WHITE
        ));
        scorePanel.add(controlGroup);
        scorePanel.add(Box.createVerticalStrut(20));

        for (int i = 0; i < contestants.length; i++) {
            contestants[i] = new Contestant("Contestant " + (i + 1));
            JPanel playerPanel = createContestantPanel(contestants[i]);
            scorePanel.add(playerPanel);
            scorePanel.add(Box.createVerticalStrut(8));
        }

        frame.add(scorePanel, BorderLayout.EAST);
    }

    private JPanel createContestantPanel(Contestant c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(highlightBlue);
        p.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        c.scoreLabel = new JLabel("<html><center>" + c.name + "<br>$" + c.score + "</center></html>", SwingConstants.CENTER);
        c.scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        c.scoreLabel.setForeground(gold);
        p.add(c.scoreLabel, BorderLayout.CENTER);
        return p;
    }

    private void createBoardPlaceholder() {
        boardPanel = new JPanel(new BorderLayout());
        boardPanel.setBackground(darkBlue);
        JLabel label = new JLabel("<html><center><font color='white'>Load a CSV file to start the game.</font></center></html>", SwingConstants.CENTER);
        label.setFont(new Font("SansSerif", Font.BOLD, 18));
        boardPanel.add(label, BorderLayout.CENTER);
        frame.add(boardPanel, BorderLayout.CENTER);
    }

    private void adjustScoreDialog() {
        Object[] options = Arrays.stream(contestants).map(c -> c.name).toArray();
        int choice = JOptionPane.showOptionDialog(frame, "Whose score to adjust?", "Adjust Score",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice >= 0) {
            Contestant selected = contestants[choice];
            String newScoreStr = JOptionPane.showInputDialog(frame,
                    "Enter new score for " + selected.name + ":", selected.score);
            if (newScoreStr != null && !newScoreStr.isBlank()) {
                try {
                    selected.score = Integer.parseInt(newScoreStr.trim());
                    updateScoreLabels();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame, "Invalid number. Score unchanged.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void loadCluesDialog() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                loadCluesFromCSV(chooser.getSelectedFile());
                assignDailyDouble();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Failed to load: " + ex.getMessage());
            }
        }
    }

    private void loadCluesFromCSV(File file) throws IOException {
        byCategory.clear();
        categoryOrder.clear();
        allClues.clear();
        finalJeopardyClue = null;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean inFinal = false;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("# FinalJeopardy")) {
                    inFinal = true;
                    continue;
                }
                if (inFinal) {
                    String[] parts = parseCSVLine(line);

                    if (parts.length >= 4 && !parts[2].isBlank() && !parts[3].isBlank())
                        finalJeopardyClue = new Clue(parts[0].trim(), 0, parts[2].trim(), parts[3].trim());
                    break;
                }
                if (line.toLowerCase().contains("category")) continue;
                String[] parts = parseCSVLine(line);

                if (parts.length < 4 || parts[1].isBlank()) continue;
                String cat = parts[0].trim();
                int val = Integer.parseInt(parts[1].trim());
                Clue clue = new Clue(cat, val, parts[2].trim(), parts[3].trim());
                byCategory.computeIfAbsent(cat, k -> {
                    categoryOrder.add(k);
                    return new ArrayList<>();
                }).add(clue);
                allClues.add(clue);
            }
        }
        for (var cat : categoryOrder)
            byCategory.get(cat).sort(Comparator.comparingInt(c -> c.value));
    }

    private void assignDailyDouble() {
        if (!allClues.isEmpty())
            allClues.get(rand.nextInt(allClues.size())).isDailyDouble = true;
    }

    private void rebuildBoard() {
        frame.remove(boardPanel);

        int categories = categoryOrder.size();
        int rows = byCategory.values().stream().mapToInt(List::size).max().orElse(5);

        // ===== Custom gradient panel for the board background =====
        JPanel boardBackground = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                int width = getWidth();
                int height = getHeight();

                // Gentle vertical gradient (dark navy -> soft blue)
                Color top = new Color(0, 0, 90);
                Color bottom = new Color(20, 20, 140);
                GradientPaint gp = new GradientPaint(0, 0, top, 0, height, bottom);
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, width, height);
            }
        };
        boardBackground.setBorder(BorderFactory.createLineBorder(Color.BLACK, 4));

        // Smaller gaps for tighter Jeopardy-style grid
        JPanel grid = new JPanel(new GridLayout(rows + 1, categories, 2, 2));
        grid.setOpaque(false); // allow gradient to show through gaps
        grid.setBackground(new Color(0, 0, 0, 0)); // transparent background

        // ===== CATEGORY HEADERS =====
        for (String cat : categoryOrder) {
            JLabel lbl = new JLabel("<html><center>" + wrapText(cat.toUpperCase(), 12) + "</center></html>", SwingConstants.CENTER);
            lbl.setFont(getScaledFont(0.035, 20));
            lbl.setForeground(Color.WHITE);
            lbl.setOpaque(true);
            lbl.setBackground(new Color(0, 0, 120));
            lbl.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(255, 215, 0), 3),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            grid.add(lbl);
        }

        // ===== CLUE CELLS =====
        for (int r = 0; r < rows; r++) {
            for (String cat : categoryOrder) {
                List<Clue> list = byCategory.get(cat);
                if (r < list.size()) {
                    Clue clue = list.get(r);
                    JButton btn = createGameButton("$" + clue.value, 48, new Color(0, 0, 120), gold);
                    btn.setFont(getScaledFont(0.04, 24));
                    btn.setBackground(new Color(0, 0, 120));
                    btn.setForeground(gold);
                    btn.setFocusPainted(false);
                    btn.setBorder(BorderFactory.createLineBorder(new Color(10, 10, 60), 4));
                    btn.setMargin(new Insets(10, 10, 10, 10));

                    // Subtle hover highlight
                    Color baseColor = btn.getBackground();
                    btn.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mouseEntered(java.awt.event.MouseEvent e) {
                            btn.setBackground(baseColor.brighter());
                        }

                        @Override
                        public void mouseExited(java.awt.event.MouseEvent e) {
                            btn.setBackground(baseColor);
                        }
                    });

                    btn.addActionListener(_ -> showClue(clue, btn));
                    grid.add(btn);
                } else {
                    JPanel empty = new JPanel();
                    empty.setOpaque(true);
                    empty.setBackground(new Color(0, 0, 120));
                    empty.setBorder(BorderFactory.createLineBorder(new Color(10, 10, 60), 4));
                    grid.add(empty);
                }
            }
        }

        boardBackground.add(grid, BorderLayout.CENTER);

        boardPanel = boardBackground;

        frame.add(boardPanel, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();
    }



    private void showClue(Clue clue, JButton sourceBtn) {
        if (clue.asked) return;
        clue.asked = true;
        sourceBtn.setEnabled(false);
        sourceBtn.setBackground(new Color(30, 30, 60)); // dim used clue
        sourceBtn.setForeground(Color.GRAY);

        // Remove hover listeners so the color doesn't change back
        for (MouseListener ml : sourceBtn.getMouseListeners()) {
            sourceBtn.removeMouseListener(ml);
        }

        if (clue.isDailyDouble) showDailyDouble(clue);
        else displayQuestion(clue);
    }

    private void showDailyDouble(Clue clue) {
        // Simple splash screen first
        JPanel ddPanel = new JPanel(new BorderLayout());
        ddPanel.setBackground(darkBlue);

        JLabel ddLabel = new JLabel("DAILY DOUBLE!", SwingConstants.CENTER);
        ddLabel.setFont(getScaledFont(0.12, 60));
        ddLabel.setForeground(gold);
        ddPanel.add(ddLabel, BorderLayout.CENTER);

        frame.setContentPane(ddPanel);
        frame.revalidate();
        frame.repaint();

        // Show splash briefly before showing contestant buttons inline
        javax.swing.Timer ddTimer = new javax.swing.Timer(1500, _ -> {
            // Transition into the inline contestant chooser version of the clue screen
            showDailyDoubleInlineChooser(clue);
        });
        ddTimer.setRepeats(false);
        ddTimer.start();
    }

    private void showDailyDoubleInlineChooser(Clue clue) {
        JPanel questionPanel = new JPanel(new BorderLayout());
        questionPanel.setBackground(darkBlue);

        JLabel qLabel = new JLabel("<html><center>DAILY DOUBLE<br><br>Who found it?</center></html>", SwingConstants.CENTER);
        qLabel.setFont(getGameFont(Math.max((int)(frame.getHeight() * 0.08), 48), false));
        qLabel.setForeground(Color.WHITE);
        qLabel.setBorder(BorderFactory.createEmptyBorder(40, 80, 40, 80));
        questionPanel.add(qLabel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setBackground(darkBlue);

        for (Contestant c : contestants) {
            JButton cBtn = new JButton(c.name);
            cBtn.setFont(new Font("SansSerif", Font.BOLD, 22));
            cBtn.addActionListener(_ -> handleDailyDoubleWager(clue, c));
            bottomPanel.add(cBtn);
        }

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setFont(new Font("SansSerif", Font.BOLD, 22));
        cancelBtn.addActionListener(_ -> backToBoard());
        bottomPanel.add(cancelBtn);

        questionPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.setContentPane(questionPanel);
        frame.revalidate();
        frame.repaint();
    }

    private void handleDailyDoubleWager(Clue clue, Contestant c) {
        // Simple popup for entering wager
        String wagerStr = JOptionPane.showInputDialog(frame,
                c.name + ", enter your wager (0–" + c.score + "):",
                "Daily Double Wager", JOptionPane.PLAIN_MESSAGE);

        int wager;
        try {
            wager = Integer.parseInt(wagerStr.trim());
            wager = Math.max(0, Math.min(wager, Math.max(1000, c.score)));
        } catch (Exception ex) {
            wager = 0;
        }
        clue.value = wager;

        // After setting wager, display the question
        displayQuestion(clue, c);
    }

    private void displayQuestion(Clue clue) {
        displayQuestion(clue, null);
    }

    private void displayQuestion(Clue clue, Contestant dailyDoubleContestant) {
        JPanel questionPanel = new JPanel(new BorderLayout());
        questionPanel.setBackground(darkBlue);

        JLabel qLabel = new JLabel("<html><center>" + clue.question.toUpperCase() + "</center></html>", SwingConstants.CENTER);
        qLabel.setFont(getGameFont(Math.max((int)(frame.getHeight() * 0.08), 48), false));
        qLabel.setForeground(Color.WHITE);
        qLabel.setBorder(BorderFactory.createEmptyBorder(40, 80, 40, 80));
        questionPanel.add(qLabel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setBackground(darkBlue);

        // If this is a Daily Double question for a specific contestant
        if (dailyDoubleContestant != null) {
            JButton correctBtn = createGameButton("Correct", 22, new Color(34, 177, 76), Color.WHITE); // green
            correctBtn.addActionListener(_ -> {
                dailyDoubleContestant.score += clue.value;
                updateScoreLabels();
                showAnswerScreen(clue);
            });

            JButton incorrectBtn = createGameButton("Incorrect", 22, new Color(200, 0, 0), Color.WHITE); // red
            incorrectBtn.addActionListener(_ -> {
                dailyDoubleContestant.score -= clue.value;
                updateScoreLabels();
                showAnswerScreen(clue);
            });

            bottomPanel.add(correctBtn);
            bottomPanel.add(incorrectBtn);
        } else {
            // Normal question flow (Buzz In / No Buzz)
            JButton buzzBtn = createGameButton("Buzz In", 22, null, null);
            buzzBtn.addActionListener(_ -> showBuzzOptions(clue, questionPanel));

            JButton noBuzzBtn = createGameButton("No Buzz", 22, null, null);
            noBuzzBtn.addActionListener(_ -> backToBoard());

            bottomPanel.add(buzzBtn);
            bottomPanel.add(noBuzzBtn);
        }

        questionPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.setContentPane(questionPanel);
        frame.revalidate();
        frame.repaint();
    }


    private JPanel createBuzzBar(Clue clue, JPanel questionPanel) {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setBackground(darkBlue);

        JButton buzzBtn = new JButton("Buzz In");
        buzzBtn.setFont(new Font("SansSerif", Font.BOLD, 22));
        buzzBtn.addActionListener(_ -> showBuzzOptions(clue, questionPanel));

        JButton noBuzzBtn = new JButton("No Buzz");
        noBuzzBtn.setFont(new Font("SansSerif", Font.BOLD, 22));
        noBuzzBtn.addActionListener(_ -> backToBoard());

        bottomPanel.add(buzzBtn);
        bottomPanel.add(noBuzzBtn);
        return bottomPanel;
    }

    private void showBuzzOptions(Clue clue, JPanel questionPanel) {
        // Create styled bottom bar with contestant buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        bottomPanel.setBackground(darkBlue);

        // Add a button for each contestant, using same style helper
        for (Contestant c : contestants) {
            JButton cBtn = createGameButton(c.name, 22, null, null);
            cBtn.addActionListener(_ -> handleAnswerAttempt(clue, c, questionPanel));
            bottomPanel.add(cBtn);
        }

        // Add Cancel button (same look)
        JButton cancelBtn = createGameButton("Cancel", 22, null, null);
        cancelBtn.addActionListener(_ -> {
            // Restore the Buzz/No Buzz bar cleanly
            removeSouthComponent(questionPanel);
            JPanel newBottom = createBuzzBar(clue, questionPanel);
            questionPanel.add(newBottom, BorderLayout.SOUTH);
            frame.revalidate();
            frame.repaint();
        });
        bottomPanel.add(cancelBtn);

        // Replace current bottom bar
        removeSouthComponent(questionPanel);
        questionPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.revalidate();
        frame.repaint();
    }

    /**
     * Helper method to safely remove the SOUTH component of a BorderLayout panel.
     */
    private void removeSouthComponent(JPanel panel) {
        BorderLayout layout = (BorderLayout) panel.getLayout();
        Component southComp = layout.getLayoutComponent(BorderLayout.SOUTH);
        if (southComp != null) {
            panel.remove(southComp);
        }
    }

    private void handleAnswerAttempt(Clue clue, Contestant c, JPanel questionPanel) {
        // Build an inline confirmation bar (no popups)
        JPanel confirmBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 10));
        confirmBar.setBackground(darkBlue);

        JLabel prompt = new JLabel("Did " + c.name + " answer correctly?");
        prompt.setForeground(Color.WHITE);
        prompt.setFont(new Font("SansSerif", Font.BOLD, 18));

        JButton correctBtn = createGameButton("Correct", 18, new Color(34, 177, 76), Color.WHITE);
        JButton incorrectBtn = createGameButton("Incorrect", 18, new Color(200, 0, 0), Color.WHITE);
        JButton passBtn = createGameButton("Pass", 16, null, null);

        // Correct: award points, show answer screen
        correctBtn.addActionListener(_ -> {
            c.score += clue.value;
            updateScoreLabels();
            showAnswerScreen(clue);
        });

        // Incorrect: deduct points, return to same question (allow other contestants to buzz)
        incorrectBtn.addActionListener(_ -> {
            c.score -= clue.value;
            updateScoreLabels();
            // Re-display the same question so others can buzz in
            displayQuestion(clue);
        });

        // Pass/Cancel: return to the original Buzz/No Buzz bar
        passBtn.addActionListener(_ -> {
            // Recreate the Buzz/No Buzz bar in the questionPanel
            JPanel newBottom = createBuzzBar(clue, questionPanel);
            // remove current bottom component (the confirmBar) and add the original bottom
            // safer remove: find SOUTH component and remove it
            questionPanel.remove(1);
            questionPanel.add(newBottom, BorderLayout.SOUTH);
            frame.revalidate();
            frame.repaint();
        });

        confirmBar.add(prompt);
        confirmBar.add(correctBtn);
        confirmBar.add(incorrectBtn);
        confirmBar.add(passBtn);

        // Replace the existing bottom component with our confirmation bar
        // (your code previously used questionPanel.remove(1); questionPanel.add(...))
        questionPanel.remove(1);
        questionPanel.add(confirmBar, BorderLayout.SOUTH);
        frame.revalidate();
        frame.repaint();
    }


    private void showAnswerScreen(Clue clue) {
        JPanel answerPanel = new JPanel(new BorderLayout());
        answerPanel.setBackground(darkBlue);
        JLabel aLabel = new JLabel(clue.answer.toUpperCase(), SwingConstants.CENTER);
        aLabel.setFont(getGameFont(Math.max((int)(frame.getHeight() * 0.09), 48), false));
        aLabel.setForeground(Color.WHITE);
        answerPanel.add(aLabel, BorderLayout.CENTER);
        frame.setContentPane(answerPanel);
        frame.revalidate();
        frame.repaint();

        javax.swing.Timer backTimer = new javax.swing.Timer(2000, _ -> {
            backToBoard();
            if (allClues.stream().allMatch(c -> c.asked))
                showWinnerOverlay();
        });
        backTimer.setRepeats(false);
        backTimer.start();
    }

    private void showWinnerOverlay() {
        Contestant winner = Arrays.stream(contestants).max(Comparator.comparingInt(c -> c.score)).get();
        JPanel winnerPanel = new JPanel(new BorderLayout());
        winnerPanel.setBackground(darkBlue);
        JLabel winnerLabel = new JLabel("<html><center>🏆 WINNER:<br>" + winner.name.toUpperCase() + "</center></html>", SwingConstants.CENTER);
        winnerLabel.setFont(getGameFont(64, true));
        winnerLabel.setForeground(gold);
        winnerPanel.add(winnerLabel, BorderLayout.CENTER);
        frame.setContentPane(winnerPanel);
        frame.revalidate();
        frame.repaint();
        new javax.swing.Timer(3000, _ -> backToBoard()).start();
    }

    private Contestant chooseContestantDialog(String prompt) {
        Object[] options = Arrays.stream(contestants).map(c -> c.name).toArray();
        int i = JOptionPane.showOptionDialog(frame, prompt, "Buzz In", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        return i >= 0 ? contestants[i] : null;
    }

    private void updateScoreLabels() {
        for (Contestant c : contestants)
            c.scoreLabel.setText("<html><center>" + c.name + "<br>$" + c.score + "</center></html>");
    }

    private void getContestantNames() {
        for (int i=0;i<contestants.length;i++) {
            String name = JOptionPane.showInputDialog(frame, "Name for contestant " + (i+1) + ":", contestants[i].name);
            if (name != null && !name.isBlank()) contestants[i].name = name.trim();
        }
        updateScoreLabels();
    }

    private void backToBoard() {
        frame.setContentPane(new JPanel(new BorderLayout()) {{ add(boardPanel, BorderLayout.CENTER); add(scorePanel, BorderLayout.EAST); }});
        frame.revalidate();
        frame.repaint();
    }

    private void showFinalJeopardyCategorySlide() {
        if (finalJeopardyClue == null) {
            JOptionPane.showMessageDialog(frame, "No Final Jeopardy question in file.");
            return;
        }

        JPanel categoryPanel = new JPanel(new BorderLayout());
        categoryPanel.setBackground(darkBlue);

        // Use the category from the CSV, not hardcoded "Final Jeopardy"
        JLabel catLabel = new JLabel("<html><center>CATEGORY<br><br>"
                + wrapText(finalJeopardyClue.category.toUpperCase(), 20) + "</center></html>", SwingConstants.CENTER);
        catLabel.setFont(getScaledFont(0.09, 48));
        catLabel.setForeground(Color.WHITE);
        categoryPanel.add(catLabel, BorderLayout.CENTER);

        // Ready button
        JButton readyBtn = new JButton("Ready");
        readyBtn.setFont(new Font("SansSerif", Font.BOLD, 28));
        readyBtn.addActionListener(_ -> showFinalJeopardyQuestion());
        JPanel bottomPanel = new JPanel();
        bottomPanel.setBackground(darkBlue);
        bottomPanel.add(readyBtn);
        categoryPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.setContentPane(categoryPanel);
        frame.revalidate();
        frame.repaint();
    }


    private void showFinalJeopardyQuestion() {
        JPanel finalPanel = new JPanel(new BorderLayout());
        finalPanel.setBackground(darkBlue);

        JLabel qLabel = new JLabel("<html><center>FINAL JEOPARDY<br><br>"
                + wrapText(finalJeopardyClue.question.toUpperCase(), 40) + "</center></html>", SwingConstants.CENTER);
        qLabel.setFont(getGameFont(Math.max((int)(frame.getHeight() * 0.085), 44), false));
        qLabel.setForeground(Color.WHITE);
        finalPanel.add(qLabel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setBackground(darkBlue);

        JButton readyBtn = new JButton("Ready");
        readyBtn.setFont(new Font("SansSerif", Font.BOLD, 22));
        readyBtn.addActionListener(_ -> handleFinalJeopardyAnswers());

        bottomPanel.add(readyBtn);
        finalPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.setContentPane(finalPanel);
        frame.revalidate();
        frame.repaint();
    }

    private void handleFinalJeopardyAnswers() {
        if (finalJeopardyClue == null) {
            JOptionPane.showMessageDialog(frame, "No Final Jeopardy question loaded.");
            return;
        }

        // Sort contestants by score ascending (lowest → highest)
        Contestant[] order = Arrays.copyOf(contestants, contestants.length);
        Arrays.sort(order, Comparator.comparingInt(c -> c.score));

        Map<Contestant, Integer> wagers = new HashMap<>();

        // ===== Get wagers and correctness sequentially =====
        for (Contestant c : order) {
            if (c.score <= 0) {
                JOptionPane.showMessageDialog(frame, c.name + " has a non-positive score and cannot participate in Final Jeopardy.");
                wagers.put(c, 0);
                continue;
            }

            String input = JOptionPane.showInputDialog(
                    frame,
                    c.name + ", enter your Final Jeopardy wager (0–" + c.score + "):",
                    "Final Jeopardy Wager",
                    JOptionPane.PLAIN_MESSAGE
            );

            int wager;
            try {
                wager = Integer.parseInt(input.trim());
                wager = Math.max(0, Math.min(wager, c.score));
            } catch (Exception e) {
                wager = 0;
            }
            wagers.put(c, wager);

            int correct = JOptionPane.showConfirmDialog(
                    frame,
                    finalJeopardyClue.answer + "\n\nDid " + c.name + " answer correctly?",
                    "Final Jeopardy Result",
                    JOptionPane.YES_NO_OPTION
            );

            if (correct == JOptionPane.YES_OPTION)
                c.score += wager;
            else
                c.score -= wager;

            updateScoreLabels();
        }

        // ===== Dramatic Full-Screen Results Display =====
        Contestant winner = Arrays.stream(contestants)
                .max(Comparator.comparingInt(c -> c.score))
                .orElse(null);

        // Animated shimmer background panel
        JPanel finalePanel = new JPanel(new BorderLayout()) {
            float shimmerPhase = 0f;

            {
                // Timer for slow shimmer effect
                new javax.swing.Timer(80, _ -> {
                    shimmerPhase += 0.01f;
                    if (shimmerPhase > 1f) shimmerPhase = 0f;
                    repaint();
                }).start();
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                int w = getWidth(), h = getHeight();

                // Deep blue vertical gradient
                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(0, 0, 70),
                        0, h, new Color(15, 15, 120)
                );
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, w, h);

                // Moving gold shimmer (radial glow)
                int cx = (int) (w / 2 + Math.sin(shimmerPhase * 2 * Math.PI) * w / 4);
                int cy = (int) (h / 2 + Math.cos(shimmerPhase * 2 * Math.PI) * h / 6);
                RadialGradientPaint rgp = new RadialGradientPaint(
                        new Point(cx, cy), w / 2f,
                        new float[]{0f, 1f},
                        new Color[]{new Color(255, 215, 0, 60), new Color(0, 0, 0, 0)}
                );
                g2d.setPaint(rgp);
                g2d.fillRect(0, 0, w, h);
            }
        };
        finalePanel.setBackground(Color.BLACK);

        // Title
        JLabel title = new JLabel("FINAL SCORES", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(getGameFont(60, true));
        title.setBorder(BorderFactory.createEmptyBorder(40, 0, 20, 0));
        finalePanel.add(title, BorderLayout.NORTH);

        // Scores area
        JPanel scoresPanel = new JPanel();
        scoresPanel.setOpaque(false);
        scoresPanel.setLayout(new BoxLayout(scoresPanel, BoxLayout.Y_AXIS));

        Arrays.stream(contestants)
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .forEach(c -> {
                    JLabel lbl = new JLabel(c.name + ": $" + c.score, SwingConstants.CENTER);
                    lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
                    lbl.setFont(getGameFont(48, true));
                    lbl.setForeground(c == winner ? new Color(255, 215, 0) : Color.WHITE);
                    lbl.setVisible(false); // initially hidden for dramatic reveal
                    scoresPanel.add(lbl);
                    scoresPanel.add(Box.createVerticalStrut(20));
                });

        finalePanel.add(scoresPanel, BorderLayout.CENTER);

        // Winner label (hidden until final reveal)
        JLabel winnerLabel = new JLabel(winner.name.toUpperCase() + " IS THE CHAMPION!", SwingConstants.CENTER);
        winnerLabel.setForeground(new Color(255, 215, 0));
        winnerLabel.setFont(getGameFont(64, true));
        winnerLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 40, 0));
        winnerLabel.setVisible(false);
        finalePanel.add(winnerLabel, BorderLayout.SOUTH);

        frame.setContentPane(finalePanel);
        frame.revalidate();
        frame.repaint();

        // Step-by-step reveal effect
        Timer revealTimer = new Timer(900, null);
        final int[] step = {0};
        revealTimer.addActionListener(_ -> {
            if (step[0] < scoresPanel.getComponentCount()) {
                Component comp = scoresPanel.getComponent(step[0]);
                comp.setVisible(true);
                scoresPanel.revalidate();
                scoresPanel.repaint();
            } else if (step[0] == scoresPanel.getComponentCount()) {
                winnerLabel.setVisible(true);
                scoresPanel.revalidate();
                scoresPanel.repaint();
            } else {
                revealTimer.stop();
            }
            step[0]++;
        });

        revealTimer.start();

        // ===== Wait for user click to continue =====
        finalePanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                backToBoard();
            }
        });
    }


    // Inner classes
    class Clue {
        String category, question, answer;
        int value;
        boolean asked = false;
        boolean isDailyDouble = false;

        Clue(String category, int value, String question, String answer) {
            this.category = category;
            this.value = value;
            this.question = question;
            this.answer = answer;
        }
    }

    class Contestant {
        String name;
        int score = 0;
        JLabel scoreLabel;
        Contestant(String name) { this.name = name; }
    }

    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes; // toggle quote state
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString().trim());
        return fields.toArray(new String[0]);
    }

    /**
     * Wrap text every N characters by inserting <br> tags.
     */
    private String wrapText(String text, int maxCharsPerLine) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String word : text.split(" ")) {
            sb.append(word).append(" ");
            count += word.length() + 1;
            if (count >= maxCharsPerLine) {
                sb.append("<br>");
                count = 0;
            }
        }
        return sb.toString().trim();
    }

    private void enableDynamicFontScaling(JPanel targetPanel) {
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int width = frame.getWidth();
                int height = frame.getHeight();

                // Scale font size roughly proportionally to window width
                int baseSize = Math.min(width, height) / 40; // adjust divisor for tuning
                Font dynamicFont = getGameFont(baseSize, true);

                // Apply scaled font to all components in the board
                updateFontRecursively(targetPanel, dynamicFont);
            }
        });
    }

    private void updateFontRecursively(Component comp, Font font) {
        comp.setFont(font);
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                updateFontRecursively(child, font);
            }
        }
    }

    private void smoothTransition(JPanel newPanel) {
        newPanel.setOpaque(false);
        JLayeredPane layered = frame.getLayeredPane();
        layered.add(newPanel, Integer.valueOf(1));
        newPanel.setBounds(0, 0, frame.getWidth(), frame.getHeight());

        Timer t = new Timer(20, null);
        final float[] alpha = {0f};
        t.addActionListener(_ -> {
            alpha[0] += 0.05f;
            if (alpha[0] >= 1f) {
                frame.setContentPane(newPanel);
                layered.remove(newPanel);
                t.stop();
            } else {
                newPanel.setOpaque(true);
                newPanel.repaint();
            }
        });
        t.start();
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new JeopardyGUI().start());
    }
}
