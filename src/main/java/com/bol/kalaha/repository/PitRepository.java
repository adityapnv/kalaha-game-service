package com.bol.kalaha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bol.kalaha.model.Pit;

/**
 * Repository to interact with Pit Entity
 */
public interface PitRepository extends JpaRepository<Pit, Long> {

  /**
   * Get the sum of Stones in list of Pits
   *
   * @param gameId     the game id
   * @param pitIndexes the pit indexes
   * @return sum of stones in player pit
   */
  @Query("SELECT SUM(p.numberOfStones) FROM Pit p WHERE p.game.id=:gameId AND p.pitIndex IN :pitIndexes ")
  Integer getSumOfStonesInPlayerPit(@Param("gameId") Long gameId, @Param("pitIndexes") List<Integer> pitIndexes);

  /**
   * Get a Individual pit based on game and pit Index
   *
   * @param gameId   the game id
   * @param pitIndex the pit index
   * @return pit by game id and pit index
   */
  @Query("SELECT p FROM Pit p WHERE p.game.id=:gameId AND p.pitIndex=:pitIndex")
  Optional<Pit> getPitByGameIdAndPitIndex(@Param("gameId") Long gameId, @Param("pitIndex") Integer pitIndex);

}
