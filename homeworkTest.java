import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;

public class homeworkTest {

    public static final char BLACK = 'X';
    public static final char WHITE = 'O';
    public static final char EMPTY = '.';
    public static final int BOARD_DIMENSION = 12;
    private static int MAX_DEPTH = 4;
    private static final int WIN_SCORE = 1000000000;
    public static char opponentColor;
    public static int[][] positionsScores;

    public static char[][] board = new char[BOARD_DIMENSION][BOARD_DIMENSION];
    public static char currentPlayer;
    public static double myTime, opponentTime;
    private static int opponentDepth = -1;


    public static void main(String[] args) {
        try {
            long startTime = System.nanoTime(); // comment out later
            Scanner scanner = new Scanner(new File("input.txt"));
            currentPlayer = scanner.nextLine().trim().charAt(0);
            opponentColor = opponentOf(currentPlayer);
            String[] times = scanner.nextLine().trim().split(" ");
            myTime = Double.parseDouble(times[0]);
            opponentTime = Double.parseDouble(times[1]);
            for (int i = 0; i < BOARD_DIMENSION; i++) {
                String line = scanner.nextLine().trim();
                for (int j = 0; j < BOARD_DIMENSION; j++) {
                    board[i][j] = line.charAt(j);
                }
            }
            scanner.close();
//            printBoard();
            initializePositionScores();
            char[][] savedBoard = readBoardStateBeforeMyLastMove();
            if (myTime > 90 || myTime % 15 == 0) {
                opponentDepth = compareBoardStatesAndEstimateOpponentDepth(board, savedBoard);
            } else {
                opponentDepth = readOpponentsDepthFromPlaydata();
            }
            adjustDepthBasedOnHeuristic();
            int[] bestMove = alphaBeta(currentPlayer, MAX_DEPTH, Integer.MIN_VALUE, Integer.MAX_VALUE, board);
            long endTime = System.nanoTime(); // comment out later
            long timeDiff = (endTime - startTime) / 1000000000; // comment out later
            FileWriter outputFile = new FileWriter("output.txt");
            outputFile.write("" + (char)(bestMove[1] + 'a') + (bestMove[0] + 1));
            outputFile.close();
            FileWriter newInputFile = new FileWriter("./input.txt");
            newInputFile.write(opponentColor + "\n");
            newInputFile.write(opponentTime + " " + (myTime - timeDiff) + "\n");
            char[][] newBoard = playMove(currentPlayer, bestMove[0], bestMove[1], board);
            for (int i = 0; i < BOARD_DIMENSION; i++) {
                for (int j = 0; j < BOARD_DIMENSION; j++) {
                    newInputFile.write(newBoard[i][j]);
                }
                newInputFile.write("\n");
            }
            newInputFile.close();
            recordMyLastMoveAndBoardState(bestMove[0], bestMove[1]); // comment out later
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + e);
        } catch (IOException e) {
            System.err.println("IOException: " + e);
        }  catch (Exception e) {
            System.err.println("Exception: " + e);
        }
    }

    public static void printBoard() {
        System.out.println("  a b c d e f g h i j k l");
        for (int i = 0; i < 12; i++) {
            System.out.print(i + 1 + " ");
            for (int j = 0; j < 12; j++) {
                System.out.print(board[i][j] + " ");
            }
            System.out.println();
        }
    }

    public static int[] alphaBeta(char player, int depth, int alpha, int beta, char[][] board) {
        if (depth == 0 || isGameOver(board)) {
            int score = evaluateBoard(board, player);
//            numBoardsEvaluated++; // Increment the count of evaluated boards.
            return new int[]{-1, -1, score};
        }

        List<int[]> validMoves = getValidMoves(player, board);
        int bestRow = -1;
        int bestCol = -1;
        int score = (player == currentPlayer) ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int[] fallbackMove = null; // Initialize a fallback move.

        for (int[] move : validMoves) {
            if (!isMoveValid(player, move[0], move[1], board)) {
                continue; // Skip invalid moves.
            }
            char[][] newBoard = playMove(player, move[0], move[1], board);
            int[] childResult = alphaBeta(opponentOf(player), depth - 1, alpha, beta, newBoard);
            int eval = childResult[2];

            // Update fallback move with the first valid move encountered.
            if (fallbackMove == null) {
                fallbackMove = new int[]{move[0], move[1], eval};
            }

            if (player == currentPlayer && eval > score) {
                score = eval;
                bestRow = move[0];
                bestCol = move[1];
                alpha = Math.max(alpha, score);
            } else if (player != currentPlayer && eval < score) {
                score = eval;
                bestRow = move[0];
                bestCol = move[1];
                beta = Math.min(beta, score);
            }

            if (beta <= alpha) {
                break; // Alpha-beta pruning.
            }
        }

        // If no best move was found (still -1), use the fallback move if available.
        if (bestRow == -1 && bestCol == -1 && fallbackMove != null) {
            return fallbackMove;
        }

        return new int[]{bestRow, bestCol, score};
    }

    public static boolean isGameOver(char[][] board) {
        // Check if the board is full
        for (int row = 0; row < BOARD_DIMENSION; row++) {
            for (int col = 0; col < BOARD_DIMENSION; col++) {
                if (board[row][col] == EMPTY) {
                    // If there's an empty spot, check if either player can make a valid move
                    if (hasValidMoves(BLACK, board) || hasValidMoves(WHITE, board)) {
                        return false; // Game is not over if a valid move exists
                    }
                    break; // No need to check further if a valid move is found
                }
            }
        }
        // If the loop completes without finding a valid move, the game is over
        return true;
    }

    private static void adjustPositionScoresForGameStage(int emptySpots) {
        // Example thresholds might need to be adjusted based on BOARD_DIMENSION
        final int earlyGameThreshold = BOARD_DIMENSION * BOARD_DIMENSION * 3 / 4; // 75% of the board is empty
        final int lateGameThreshold = BOARD_DIMENSION * BOARD_DIMENSION / 4; // 25% of the board is empty

        // Reset to default values
        initializePositionScores();

        if (emptySpots > earlyGameThreshold) {
            // Early game: higher emphasis on corners and edges
            applyEarlyGameWeights();
        } else if (emptySpots <= earlyGameThreshold && emptySpots > lateGameThreshold) {
            // Mid game: balanced approach
            applyMidGameWeights();
        } else {
            // Late game: focus on mobility and securing stable discs
            applyLateGameWeights();
        }
    }

    private static void applyEarlyGameWeights() {
        // Increase corner and adjacent corner penalties
        int highScore = 100;
        int highRisk = -20; // Increased penalty to avoid giving corners
        // Update corners
        positionsScores[0][0] = highScore;
        positionsScores[0][BOARD_DIMENSION - 1] = highScore;
        positionsScores[BOARD_DIMENSION - 1][0] = highScore;
        positionsScores[BOARD_DIMENSION - 1][BOARD_DIMENSION - 1] = highScore;
        // Adjust adjacent corners (example for one corner, repeat for others)
        // Top left corner
        positionsScores[0][1] = highRisk;
        positionsScores[1][0] = highRisk;
        positionsScores[1][1] = highRisk;

        // Top right corner
        positionsScores[0][BOARD_DIMENSION - 2] = highRisk;
        positionsScores[1][BOARD_DIMENSION - 1] = highRisk;
        positionsScores[1][BOARD_DIMENSION - 2] = highRisk;

        // Bottom left corner
        positionsScores[BOARD_DIMENSION - 2][0] = highRisk;
        positionsScores[BOARD_DIMENSION - 1][1] = highRisk;
        positionsScores[BOARD_DIMENSION - 2][1] = highRisk;

        // Bottom right corner
        positionsScores[BOARD_DIMENSION - 2][BOARD_DIMENSION - 2] = highRisk;
        positionsScores[BOARD_DIMENSION - 1][BOARD_DIMENSION - 2] = highRisk;
        positionsScores[BOARD_DIMENSION - 2][BOARD_DIMENSION - 1] = highRisk;

        // Similar adjustments for other corners
    }

    private static void applyMidGameWeights() {
        // Slightly decrease the penalty for being near corners to reflect a shift in strategy
        int adjacentRisk = -10; // Example adjustment
        // Top left corner
        positionsScores[0][1] = adjacentRisk;
        positionsScores[1][0] = adjacentRisk;
        positionsScores[1][1] = adjacentRisk;

        // Top right corner
        positionsScores[0][BOARD_DIMENSION - 2] = adjacentRisk;
        positionsScores[1][BOARD_DIMENSION - 1] = adjacentRisk;
        positionsScores[1][BOARD_DIMENSION - 2] = adjacentRisk;

        // Bottom left corner
        positionsScores[BOARD_DIMENSION - 2][0] = adjacentRisk;
        positionsScores[BOARD_DIMENSION - 1][1] = adjacentRisk;
        positionsScores[BOARD_DIMENSION - 2][1] = adjacentRisk;

        // Bottom right corner
        positionsScores[BOARD_DIMENSION - 2][BOARD_DIMENSION - 2] = adjacentRisk;
        positionsScores[BOARD_DIMENSION - 1][BOARD_DIMENSION - 2] = adjacentRisk;
        positionsScores[BOARD_DIMENSION - 2][BOARD_DIMENSION - 1] = adjacentRisk;
    }

    private static void applyLateGameWeights() {
        // Late game: Focus on securing as many discs as possible, mobility becomes critical
        // Example: increase scores for central positions to promote mobility
        int centralBonus = 5; // Bonus for central positions
        for (int i = 2; i < BOARD_DIMENSION - 2; i++) {
            for (int j = 2; j < BOARD_DIMENSION - 2; j++) {
                positionsScores[i][j] += centralBonus;
            }
        }
    }

    private static int determineGameStage() {
        int emptySpots = countEmptySpots();
        if (emptySpots > 114) {
            return 1; // Early game
        } else if (emptySpots > 54) {
            return 2; // Mid game
        } else {
            return 3; // Late game
        }
    }

    private static int countEmptySpots() {
        int count = 0;
        for (int i = 0; i < BOARD_DIMENSION; i++) {
            for (int j = 0; j < BOARD_DIMENSION; j++) {
                if (board[i][j] == EMPTY) count++;
            }
        }
        return count;
    }

    private static int evaluateBoard(char[][] board, char player) {
        int gameStage = determineGameStage();
        adjustPositionScoresForGameStage(countEmptySpots()); // Dynamically adjust position scores based on game stage

        int score = positionScore(board, player); // Basic positional advantage
        score += evaluateBoardByFilledRows(board, player); // Additional depth from filled rows and potential control

        // Incorporating mobility and potential mobility
        int mobility = mobilityScore(board, player);
        int potentialMobility = potentialMobilityScore(board, player);

        // Disk parity
        int diskParity = diskParityScore(board, player);

        // Adjust the weight of each component based on the game stage
        switch (gameStage) {
            case 1: // Early game
                score *= 3;
                score += mobility;
                break;
            case 2: // Mid game
                score += mobility * 2;
                score += potentialMobility;
                break;
            case 3: // Late game
                score += diskParity * 5;
                break;
        }

        return score;
    }

    private static int potentialMobilityScore(char[][] board, char player) {
        char opponent = opponentOf(player);
        int potentialMobility = 0;

        // Iterate over the board to find empty spots next to opponent's pieces
        for (int i = 0; i < BOARD_DIMENSION; i++) {
            for (int j = 0; j < BOARD_DIMENSION; j++) {
                // Only consider empty spots for potential mobility
                if (board[i][j] == EMPTY && isAdjacentToOpponent(board, i, j, opponent)) {
                    potentialMobility++;
                }
            }
        }

        return potentialMobility;
    }

    private static boolean isAdjacentToOpponent(char[][] board, int row, int col, char opponent) {
        // Directions to check around a given spot
        int[][] directions = {
                {-1, -1}, {-1, 0}, {-1, 1},
                {0, -1},          {0, 1},
                {1, -1}, {1, 0}, {1, 1}
        };

        for (int[] dir : directions) {
            int newRow = row + dir[0];
            int newCol = col + dir[1];

            // Check if the new position is within bounds
            if (newRow >= 0 && newRow < BOARD_DIMENSION && newCol >= 0 && newCol < BOARD_DIMENSION) {
                // If the adjacent spot is occupied by the opponent, this spot is considered for potential mobility
                if (board[newRow][newCol] == opponent) {
                    return true;
                }
            }
        }

        return false; // No adjacent opponent pieces found
    }

    private static int mobilityScore(char[][] board, char player) {
        int actualMobilityPlayer = getValidMoves(player, board).size();
        int actualMobilityOpponent = getValidMoves(opponentOf(player), board).size();
        return actualMobilityPlayer - actualMobilityOpponent;
    }

    private static int diskParityScore(char[][] board, char player) {
        int[] scores = currentScore(player, board);
        return scores[0] - scores[1]; // Assuming scores[0] is for the current player
    }


    private static int positionScore(char[][] board, char player) {
        int score = 0;

        for (int row = 0; row < BOARD_DIMENSION; row++) {
            for (int col = 0; col < BOARD_DIMENSION; col++) {
                if (board[row][col] == player) {
                    score += positionsScores[row][col];
                }
            }
        }

        return score;
    }

//    private static int evaluateBoard(char[][] board, int additionalPiecesPlayed) {
//        int spotsRemaining = BOARD_DIMENSION * BOARD_DIMENSION - (additionalPiecesPlayed + movesPlayed);
//        if (spotsRemaining == 0) {
//            int[] scores = currentScore(currentPlayer, board);
//            if (scores[0] > scores[1]) {
//                return WIN_SCORE;
//            } else if (scores[0] < scores[1]) {
//                return -WIN_SCORE;
//            } else {
//                return 0;
//            }
//        }
//        int[] scores = new int[128];
//        int[] numOccurrences = new int[128];
//        for (int row = 0; row < BOARD_DIMENSION; row++) {
//            for (int col = 0; col < BOARD_DIMENSION; col++) {
//                char piece = board[row][col];
//                int score = positionsScores[row][col];
//                scores[piece] += score;
//                numOccurrences[piece]++;
//            }
//        }
//        scores[currentPlayer] += evaluateBoardByFilledRows(board, currentPlayer);
//        scores[opponentColor] += evaluateBoardByFilledRows(board, opponentColor);
//        if (spotsRemaining <= 15) {
//            scores[currentPlayer] *= (1 + (numOccurrences[currentPlayer] / (spotsRemaining * 25)));
//            scores[opponentColor] *= (1 + (numOccurrences[opponentColor] / (spotsRemaining * 25)));
//        }
//        return scores[currentPlayer] - scores[opponentColor];
//    }

    private static int[] currentScore(char userPiece, char[][] board) {
        int[] scores = new int[2];
        for (char[] row : board) {
            for (char spot : row) {
                if (spot == userPiece) {
                    scores[0]++;
                } else if (spot == opponentColor) {
                    scores[1]++;
                }
            }
        }
        return scores;
    }
    private static int evaluateBoardByFilledRows(char[][] board, char color) {
        int score = 0;
        // evaluate each row to see if any of them are completely filled or almost completely filled
        for (char[] row : board) {
            int nonFriendlyCount = BOARD_DIMENSION - numberOfPieceOnBoard(color, row);
            if (nonFriendlyCount == 0) {
                score += 17;
            } else if (nonFriendlyCount == 1) {
                score += 8;
            }
        }
        // evaluate each column
        for (int colNum = 0; colNum < BOARD_DIMENSION; colNum++) {
            int nonFriendlyCount = 0;
            for (int rowNum = 0; rowNum < BOARD_DIMENSION; rowNum++) {
                if (board[rowNum][colNum] != color) {
                    nonFriendlyCount++;
                    if (nonFriendlyCount >= 2) {
                        break;
                    }
                }
            }
            if (nonFriendlyCount == 0) {
                score += 17;
            } else if (nonFriendlyCount == 1) {
                score += 8;
            }
        }
        // evaluate top left to bottom right diagonal
        int nonFriendlyCount = 0;
        for (int index = 0; index < BOARD_DIMENSION; index++) {
            if (board[index][index] != color) {
                nonFriendlyCount++;
                if (nonFriendlyCount >= 2) {
                    break;
                }
            }
        }
        if (nonFriendlyCount == 0) {
            score += 25;
        } else if (nonFriendlyCount == 1) {
            score += 16;
        }
        // evaluate top right to bottom left diagonal
        nonFriendlyCount = 0;
        for (int index = 0; index < BOARD_DIMENSION; index++) {
            if (board[index][BOARD_DIMENSION - index - 1] != color) {
                nonFriendlyCount++;
                if (nonFriendlyCount >= 2) {
                    break;
                }
            }
        }
        if (nonFriendlyCount == 0) {
            score += 25;
        } else if (nonFriendlyCount == 1) {
            score += 16;
        }
        return score;
    }

    private static int numberOfPieceOnBoard(char piece, char[] row) {
        int count = 0;
        for (char cell : row) {
            if (cell == piece) {
                count++;
            }
        }
        return count;
    }

    public static boolean isMoveValid(char player, int row, int col, char[][] board) {
        if (board[row][col] != EMPTY) return false; // Ensure the cell is empty.

        char opponent = (player == BLACK) ? WHITE : BLACK;
        // Directions to check: vertical, horizontal, diagonal
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

        for (int[] d : directions) {
            int dRow = d[0], dCol = d[1];
            int currentRow = row + dRow, currentCol = col + dCol;
            boolean foundOpponentPiece = false;

            while (currentRow >= 0 && currentRow < BOARD_DIMENSION && currentCol >= 0 && currentCol < BOARD_DIMENSION) {
                if (board[currentRow][currentCol] == opponent) {
                    foundOpponentPiece = true;
                    currentRow += dRow;
                    currentCol += dCol;
                } else if (board[currentRow][currentCol] == player && foundOpponentPiece) {
                    return true; // Valid move: flanks at least one line of opponent pieces.
                } else {
                    break; // Stop checking this direction: hit an empty spot or own piece without finding opponent's piece.
                }
            }
        }
        return false; // Didn't find any direction that flanks opponent's pieces.
    }


    public static boolean hasValidMoves(char piece, char[][] board) {
        for (int row = 0; row < BOARD_DIMENSION; row++) {
            for (int col = 0; col < BOARD_DIMENSION; col++) {
                if (isMoveValid(piece, row, col, board)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<int[]> getValidMoves(char piece, char[][] board) {
        List<int[]> validMoves = new ArrayList<>();
        for (int row = 0; row < BOARD_DIMENSION; row++) {
            for (int col = 0; col < BOARD_DIMENSION; col++) {
                if (isMoveValid(piece, row, col, board)) {
                    validMoves.add(new int[]{row, col});
                }
            }
        }
        return validMoves;
    }

    public static char[][] playMove(char player, int row, int col, char[][] originalBoard) {
        // Create a copy of the board to avoid modifying the original board
        char[][] boardCopy = new char[BOARD_DIMENSION][BOARD_DIMENSION];
        for (int i = 0; i < BOARD_DIMENSION; i++) {
            System.arraycopy(originalBoard[i], 0, boardCopy[i], 0, BOARD_DIMENSION);
        }

        // Proceed only if the move is valid
        if (isMoveValid(player, row, col, boardCopy)) {
            boardCopy[row][col] = player; // Place the piece on the board

            // Directions to check for capturing opponent pieces
            int[][] directions = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
            for (int[] dir : directions) {
                int dRow = dir[0], dCol = dir[1];
                List<int[]> piecesToFlip = new ArrayList<>();

                int currentRow = row + dRow, currentCol = col + dCol;
                while (currentRow >= 0 && currentRow < BOARD_DIMENSION && currentCol >= 0 && currentCol < BOARD_DIMENSION &&
                        boardCopy[currentRow][currentCol] != EMPTY) {
                    if (boardCopy[currentRow][currentCol] == player) {
                        // Once we reach another piece of the same color, flip the pieces in between
                        for (int[] flip : piecesToFlip) {
                            boardCopy[flip[0]][flip[1]] = player;
                        }
                        break;
                    } else {
                        piecesToFlip.add(new int[]{currentRow, currentCol});
                    }
                    currentRow += dRow;
                    currentCol += dCol;
                }
            }
        } else {
            throw new IllegalArgumentException("Invalid move at (" + row + ", " + col + ")");
        }
        return boardCopy; // Return the updated board
    }

    private static char opponentOf(char piece) {
        return (piece == BLACK) ? WHITE : BLACK;
    }


    public static void initializePositionScores() {
        positionsScores = new int[BOARD_DIMENSION][BOARD_DIMENSION];
        // Initialize with base scores for simplicity
        for (int i = 0; i < BOARD_DIMENSION; i++) {
            for (int j = 0; j < BOARD_DIMENSION; j++) {
                positionsScores[i][j] = 1; // Assign a default score for all positions
            }
        }

        // Assign scores based on the static weights heuristic for a 12x12 board
        // High value corners
        int highScore = 100;
        positionsScores[0][0] = highScore;
        positionsScores[0][BOARD_DIMENSION - 1] = highScore;
        positionsScores[BOARD_DIMENSION - 1][0] = highScore;
        positionsScores[BOARD_DIMENSION - 1][BOARD_DIMENSION - 1] = highScore;

        // High risk adjacent to corners
        int highRisk = -4;
        positionsScores[1][0] = highRisk;
        positionsScores[0][1] = highRisk;
        positionsScores[1][1] = highRisk;
        positionsScores[1][BOARD_DIMENSION - 2] = highRisk;
        positionsScores[0][BOARD_DIMENSION - 2] = highRisk;
        positionsScores[BOARD_DIMENSION - 2][0] = highRisk;
        positionsScores[BOARD_DIMENSION - 2][1] = highRisk;
        positionsScores[BOARD_DIMENSION - 2][BOARD_DIMENSION - 2] = highRisk;
        positionsScores[BOARD_DIMENSION - 2][BOARD_DIMENSION - 1] = highRisk;
        positionsScores[BOARD_DIMENSION - 1][BOARD_DIMENSION - 2] = highRisk;

        // Edges and inner squares with specific scores
        int edgeScore = 2;
        int innerHighRisk = -3;
        int innerLowRisk = -1;
        int neutralScore = 0;
        int favorableScore = 1;

        // Set edges
        for (int i = 2; i < BOARD_DIMENSION - 2; i++) {
            positionsScores[i][0] = edgeScore;
            positionsScores[i][BOARD_DIMENSION - 1] = edgeScore;
            positionsScores[0][i] = edgeScore;
            positionsScores[BOARD_DIMENSION - 1][i] = edgeScore;
        }

        // Set scores for inner positions based on the static heuristic pattern
        for (int i = 2; i < BOARD_DIMENSION - 2; i++) {
            for (int j = 2; j < BOARD_DIMENSION - 2; j++) {
                // Assign inner scores based on distance from center and edges
                if (i == 2 || i == BOARD_DIMENSION - 3 || j == 2 || j == BOARD_DIMENSION - 3) {
                    positionsScores[i][j] = innerHighRisk; // High risk near edges
                } else if (i == 3 || i == BOARD_DIMENSION - 4 || j == 3 || j == BOARD_DIMENSION - 4) {
                    positionsScores[i][j] = innerLowRisk; // Slightly less risk
                } else {
                    positionsScores[i][j] = favorableScore; // More favorable towards the center
                }
            }
        }

        // Adjust specific positions based on further strategic considerations if necessary
    }

    // Method to read the last move and board state from playdata.txt
    private static char[][] readBoardStateBeforeMyLastMove() {
        File playDataFile = new File("playdata.txt");

        // Check if the playdata.txt file exists
        if (!playDataFile.exists()) {
            return null; // Or handle differently if needed
        }

        char[][] savedBoard = new char[BOARD_DIMENSION][BOARD_DIMENSION];
        try (Scanner scanner = new Scanner(playDataFile)) {
            if (scanner.hasNextLine()) {
                scanner.nextLine(); // Skip the move line if it's there
            }
            int row = 0;
            while (scanner.hasNextLine() && row < BOARD_DIMENSION) {
                String line = scanner.nextLine();
                savedBoard[row] = line.toCharArray();
                row++;
            }
        } catch (FileNotFoundException e) {
            // This catch block might not be necessary anymore since we're checking for file existence upfront
            // But keeping it for completeness and to handle any unexpected scenarios
            System.err.println("Failed to read saved board state: " + e.getMessage());
            return null;
        }
        return savedBoard;
    }
    // Adjust the readBoardStateBeforeMyLastMove method to also read the opponent's depth
    private static int readOpponentsDepthFromPlaydata() {
        File playDataFile = new File("playdata.txt");
        if (!playDataFile.exists()) {
            return -1; // Or handle differently if needed
        }

        try (Scanner scanner = new Scanner(playDataFile)) {
            if (scanner.hasNextLine()) {
                return Integer.parseInt(scanner.nextLine()); // Read the opponent's depth
            }
        } catch (FileNotFoundException e) {
            System.err.println("Failed to read saved opponent's depth: " + e.getMessage());
        }
        return -1; // Default to -1 if unable to read
    }

    public static int compareBoardStatesAndEstimateOpponentDepth(char[][] currentBoard, char[][] savedBoard) {
        int changes = 0;
        int strategicValueDifference = 0;
        int savedStrategicValue = calculateStrategicValue(savedBoard, currentPlayer);
        int currentStrategicValue = calculateStrategicValue(currentBoard, currentPlayer);

        // Calculate the absolute difference in strategic values between the two states
        strategicValueDifference = Math.abs(currentStrategicValue - savedStrategicValue);

        for (int i = 0; i < BOARD_DIMENSION; i++) {
            for (int j = 0; j < BOARD_DIMENSION; j++) {
                if (currentBoard[i][j] != savedBoard[i][j]) {
                    changes++;
                }
            }
        }

        int stage = determineGameStage();
        // Estimation logic can be further refined based on specific game strategies and opponent behavior patterns
        if (changes == 1 && stage == 3) {
            return 2; // Likely a simple, immediate move, depth of 1
        } else if (changes <= 3 && strategicValueDifference < 20) {
            return 3; // Slightly more complex, depth of 2
        } else if ((changes > 3 && changes <= 5) || (strategicValueDifference >= 20 && strategicValueDifference < 50)) {
            return 4; // Moderate complexity, depth of 3 or 4
        } else if ((changes > 5 && changes <= 8) || (strategicValueDifference >= 50 && strategicValueDifference < 100)) {
            return 5; // Indicates a deeper analysis, depth of 4 or 5
        } else if (changes > 8 || strategicValueDifference >= 100) {
            return 6; // Highly complex move suggesting an advanced strategy, possibly depth of 5 or more
        } else {
            return 3; // Default to intermediate complexity if none of the above conditions match
        }
    }

    private static void adjustDepthBasedOnHeuristic() {
        if (opponentDepth == -1) {
            if (myTime <= 150.0 && myTime >= 60.0) {
                MAX_DEPTH = 3;
            } else if (myTime < 60.0 && myTime >= 3.0) {
                MAX_DEPTH = 2;
            } else if (myTime < 3.0) {
                MAX_DEPTH = 1;
            }
        } else {
            if (myTime >= opponentTime + 60.0 && myTime >=150) {
                MAX_DEPTH = Math.max(opponentDepth + 1, 4);
            } else if (myTime >= opponentTime + 30 && myTime >= 60 && myTime <= 150) {
                MAX_DEPTH = Math.max(opponentDepth + 1, 3);
            }  else if (myTime <= opponentTime && myTime >= 60 && myTime <= 150) {
                MAX_DEPTH = 4;
            } else if (myTime < 60.0 && myTime >= 3.0) {
                MAX_DEPTH = 5;
            } else if (myTime < 3.0) {
                MAX_DEPTH = 6;
            }
        }

    }

    public static int calculateStrategicValue(char[][] board, char player) {

        int strategicValue = 0;
        for (int i = 0; i < BOARD_DIMENSION; i++) {
            for (int j = 0; j < BOARD_DIMENSION; j++) {
                if (board[i][j] == player) {
                    // Add the strategic value of the position if it is occupied by the player
                    strategicValue += positionsScores[i][j];
                }
            }
        }
        return strategicValue;
    }

    private static void recordMyLastMoveAndBoardState(int row, int col) {
        try (FileWriter writer = new FileWriter("playdata.txt", false)) { // Overwrite mode
            writer.write(opponentDepth + "\n"); // Write the predicted depth of the opponent first
            for (char[] rowContent : board) {
                writer.write(new String(rowContent) + "\n"); // Write the board state line by line
            }
        } catch (IOException e) {
            System.err.println("Error writing to playdata.txt: " + e.getMessage());
        }
    }


}