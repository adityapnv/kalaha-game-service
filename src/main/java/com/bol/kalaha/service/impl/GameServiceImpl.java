package com.bol.kalaha.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bol.kalaha.util.KalahCommonUtil;
import com.bol.kalaha.repository.GameRepository;
import com.bol.kalaha.dto.CreateGameReply;
import com.bol.kalaha.dto.GameStatus;
import com.bol.kalaha.dto.MakeAMoveReply;
import com.bol.kalaha.exception.EmptyPitException;
import com.bol.kalaha.exception.GameOverException;
import com.bol.kalaha.exception.InvalidGameIdException;
import com.bol.kalaha.exception.InvalidPitIndexException;
import com.bol.kalaha.exception.WrongGameStatusException;
import com.bol.kalaha.model.Game;
import com.bol.kalaha.model.Pit;
import com.bol.kalaha.service.GameService;
import com.bol.kalaha.service.KalahaRuleService;

import lombok.RequiredArgsConstructor;

/**
 * The type Game service.
 */
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

  /**
   * The Game repo.
   */
  private final GameRepository gameRepo;
  
  /**
   * The Kalaha rule service.
   */
  private final KalahaRuleService kalahaRuleService;

  /**
   * Create game create game reply.
   *
   * @param url the url
   * @return the create game reply
   */
  @Override
  public CreateGameReply createGame(String url) {

    List<Pit> pits = new ArrayList<>();
    IntStream.range(KalahCommonUtil.PIT_START_INDEX, KalahCommonUtil.PIT_END_INDEX + 1).sequential()
        .forEach(index -> {

          Pit pit = new Pit();

          if (index == KalahCommonUtil.PLAYER1_HOUSE || index == KalahCommonUtil.PLAYER2_HOUSE) {
            pit.setNumberOfStones(KalahCommonUtil.INITIAL_STONE_ON_HOUSE);
          } else {
            pit.setNumberOfStones(KalahCommonUtil.INITIAL_STONE_ON_PIT);
          }
          pit.setPitIndex(index);
          pits.add(pit);
        });

    Game game = new Game(pits.toArray(Pit[]::new));
    game.setGameStatus(GameStatus.INIT);
    game = gameRepo.save(game);

    return CreateGameReply.builder().gameId(game.getId()).uri(url + "/" + game.getId()).build();
  }
  
  /**
   * Make a move by player make a move reply.
   *
   * @param gameId   the game id
   * @param pitIndex the pit index
   * @param url      the url
   * @return the make a move reply
   */
  @Transactional
  @Override
  public MakeAMoveReply makeAMoveByPlayer(Long gameId, Integer pitIndex, String url) {

    Game game = gameRepo.findById(gameId).orElseThrow(() -> new InvalidGameIdException(gameId.toString()));
    GameStatus gameStatus = game.getGameStatus();

    //If the Initial status is Init ,based on Index calculate the player turn
    if (gameStatus == GameStatus.INIT) {
      gameStatus = kalahaRuleService.getPlayerTurn(pitIndex);
    }

    //Validate if the inputs are correct
    validateTheGame(pitIndex, gameStatus);

    //Start with the Pit Index selected
    Pit selectedPit = kalahaRuleService.getPitByPitIndexAndGameId(gameId, pitIndex)
        .orElseThrow(() -> new EntityNotFoundException("Pit Entity not found :" + pitIndex));

    if (selectedPit.getNumberOfStones() == 0) {
      throw new EmptyPitException(pitIndex.toString());
    }

    int playerPitToNotPutStone = pitWherePlayerCanNotPutStone(gameStatus);
    int initialStones = selectedPit.getNumberOfStones();
    //last pit based on the stones count of Index selected
    int lastStonePit = getLastStonePit(initialStones, pitIndex);
    //if stones is more than 13 ,the number of stones goes more than 1 circle
    int numberOfStonesToPutInEachPit = getNumberOfStonesToPutInEachPit(selectedPit.getNumberOfStones());

    //put the stones takes from the Pit index in the respective Pits
    Map<Integer, Pit> mapPit = kalahaRuleService.moveTheStonesInBoard(game.getPits(), playerPitToNotPutStone,
        lastStonePit, pitIndex, numberOfStonesToPutInEachPit, initialStones);

    //if the last stone falls in Empty Pit and in Player Index the opposite Index stones are taken and
    //put in the player own kalah
    mapPit = kalahaRuleService.checkAndUpdateIfLastPitIsEmpty(mapPit, lastStonePit, gameStatus);

    //Get the next turn will whose
    GameStatus nextStatus = kalahaRuleService.getNextTurnOfPlayer(gameStatus, lastStonePit);
    game.setGameStatus(nextStatus);
    game.setPits(new ArrayList<>(mapPit.values()));
    game = gameRepo.save(game);

    //If the Game is Over when Any Player side Indexes stones get over
    game = kalahaRuleService.checkGameEnded(game);

    //if Game is finished
    if (game.getGameStatus() == GameStatus.FINISHED) {
      gameRepo.save(game);
    }

    Map<Integer, Integer> resultMap = game.getPits().stream()
        .collect(Collectors.toMap(Pit::getPitIndex, Pit::getNumberOfStones));

    return MakeAMoveReply.builder().id(gameId).uri(url + "/games/" + game.getId()).status(resultMap).build();
  }
  
  /**
   * check for the validation of the game
   *
   * @param pitIndex   the pit index
   * @param gameStatus the game status
   */
  private void validateTheGame(int pitIndex, GameStatus gameStatus) {

    if (kalahaRuleService.getPlayerTurn(pitIndex) != gameStatus) {
      throw new WrongGameStatusException(gameStatus.toString());
    }

    if (pitIndex == KalahCommonUtil.PLAYER1_HOUSE || pitIndex == KalahCommonUtil.PLAYER2_HOUSE) {
      throw new InvalidPitIndexException(String.valueOf(pitIndex));
    }

    if (gameStatus == GameStatus.FINISHED) {
      throw new GameOverException(gameStatus.toString());
    }
  }
  
  /**
   * Get the Pit where Player can't put the stone
   *
   * @param gameStatus the game status
   * @return int int
   */
  private int pitWherePlayerCanNotPutStone(GameStatus gameStatus) {
    int playerPitToNotPutStone = 0;

    if (gameStatus == GameStatus.PLAYER1_TURN) {
      playerPitToNotPutStone = KalahCommonUtil.PLAYER2_HOUSE;
    }
    if (gameStatus == GameStatus.PLAYER2_TURN) {
      playerPitToNotPutStone = KalahCommonUtil.PLAYER1_HOUSE;
    }

    return playerPitToNotPutStone;
  }
  
  /**
   * calculate to find the last pit to put stone
   *
   * @param initialStones the initial stones
   * @param pitIndex      the pit index
   * @return last stone pit
   */
  private int getLastStonePit(int initialStones, int pitIndex) {
    int lastStonePit;
    if (initialStones >= (KalahCommonUtil.PIT_END_INDEX - 1)) {
      lastStonePit = initialStones % (KalahCommonUtil.PIT_END_INDEX - 1);
      lastStonePit = lastStonePit == 0 ? pitIndex : lastStonePit;

    } else {
      lastStonePit = initialStones + pitIndex;
      lastStonePit = lastStonePit > KalahCommonUtil.PIT_END_INDEX ?
          Math.abs(lastStonePit - KalahCommonUtil.PIT_END_INDEX) :
          lastStonePit;
    }

    return lastStonePit;
  }
  
  /**
   * Calculate number of stones to put in each Pit if more than 13
   *
   * @param initialStones the initial stones
   * @return number of stones to put in each pit
   */
  private int getNumberOfStonesToPutInEachPit(int initialStones) {
    if (initialStones > KalahCommonUtil.PIT_END_INDEX - 1) {
      return initialStones / (KalahCommonUtil.PIT_END_INDEX - 1);
    } else {
      return 0;
    }
  }


}
