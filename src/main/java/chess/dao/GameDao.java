package chess.dao;

import chess.domain.board.ChessBoard;
import chess.domain.piece.Color;
import chess.domain.piece.Piece;
import chess.domain.position.Position;
import chess.dto.ChessBoardDto;
import chess.dto.PieceDeserializeTable;
import chess.dto.PieceDto;
import chess.dto.RunningGameDto;
import chess.exception.NoSavedGameException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
public class GameDao {
    private final JdbcTemplate jdbcTemplate;

    public GameDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int saveGame(Color currentTurnColor, Map<String, PieceDto> board) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String insertGameQuery = "insert into game(turn) values(?)";
        this.jdbcTemplate.update(con -> {
            PreparedStatement pstmt = con.prepareStatement(
                    insertGameQuery,
                    new String[]{"game_id"});
            pstmt.setString(1, currentTurnColor.name());
            return pstmt;
        }, keyHolder);
        int gameId = Objects.requireNonNull(keyHolder.getKey()).intValue();
        System.out.println("gameID : " + gameId);
        savePiecesByGameId(gameId, board);
        return gameId;
    }

    private void savePiecesByGameId(int gameId, Map<String, PieceDto> board) {
        for (String position : board.keySet()) {
            String query = "insert into piece(game_id, name, color, position) values(?, ?, ?, ?)";
            PieceDto piece = board.get(position);
            this.jdbcTemplate.update(query, gameId, piece.getName(), piece.getColor(), position);
        }
    }

    private final RowMapper<ChessBoard> chessBoardRowMapper = (resultSet, rowNum) -> {
        Map<Position, Piece> board = new HashMap<>();

        do {
            Piece piece = PieceDeserializeTable.deserializeFrom(
                    resultSet.getString("name"),
                    Color.of(resultSet.getString("color"))
            );
            Position position = Position.of(resultSet.getString("position"));
            board.put(position, piece);
        } while (resultSet.next());
        return ChessBoard.from(board);
    };

    private final RowMapper<Color> colorRowMapper = (resultSet, rowNum) -> Color.of(resultSet.getString("turn"));

    public RunningGameDto loadGame(int gameId) {
        String gameQuery = "SELECT turn FROM game WHERE game_id = ?";
        Color currentTurn = this.jdbcTemplate.queryForObject(gameQuery, colorRowMapper, gameId);

        if (currentTurn != null) {
            String queryPiece = "SELECT * FROM piece WHERE game_id = ?";
            ChessBoard chessBoard = this.jdbcTemplate.queryForObject(queryPiece, chessBoardRowMapper, gameId);
            return RunningGameDto.of(chessBoard, currentTurn, false);
        }
        throw new NoSavedGameException("저장된 게임이 없습니다.");
    }

    public void updatePiecesByGameId(ChessBoardDto chessBoardDto, int gameId) {
        deletePiecesByGameId(gameId);
        savePiecesByGameId(gameId, chessBoardDto.board());
    }

    public void updateTurnByGameId(Color currentTurnColor, int gameId) {
        String query = "UPDATE game set turn=? WHERE game_id = ?";
        this.jdbcTemplate.update(query, currentTurnColor.name(), gameId);
    }

    public void deleteGameByGameId(int gameId) {
        this.jdbcTemplate.update("DELETE FROM game WHERE game_id = ?", gameId);
    }

    public void deletePiecesByGameId(int gameId) {
        this.jdbcTemplate.update("DELETE FROM piece WHERE game_id = ?", gameId);
    }

    public List<Integer> loadGameList() {
        String query = "SELECT game_id FROM game ";
        return this.jdbcTemplate.query(query, (resultSet, rowNum) -> resultSet.getInt("game_id"));
    }
}
