/*
 * Designed and developed by 2022 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.karlosarr.pokedex.core.repository

import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.map
import com.skydoves.sandwich.onError
import com.skydoves.sandwich.onException
import com.skydoves.sandwich.suspendOnSuccess
import dev.karlosarr.pokedex.core.database.PokemonInfoDao
import dev.karlosarr.pokedex.core.database.entitiy.mapper.asDomain
import dev.karlosarr.pokedex.core.database.entitiy.mapper.asEntity
import dev.karlosarr.pokedex.core.model.PokemonInfo
import dev.karlosarr.pokedex.core.network.Dispatcher
import dev.karlosarr.pokedex.core.network.PokedexAppDispatchers
import dev.karlosarr.pokedex.core.network.model.PokemonErrorResponse
import dev.karlosarr.pokedex.core.network.model.mapper.ErrorResponseMapper
import dev.karlosarr.pokedex.core.network.service.PokedexClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import javax.inject.Inject

@VisibleForTesting
class DetailRepositoryImpl @Inject constructor(
  private val pokedexClient: PokedexClient,
  private val pokemonInfoDao: PokemonInfoDao,
  @Dispatcher(PokedexAppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : DetailRepository {

  @WorkerThread
  override fun fetchPokemonInfo(name: String, onComplete: () -> Unit, onError: (String?) -> Unit) =
    flow {
      val pokemonInfo = pokemonInfoDao.getPokemonInfo(name)
      if (pokemonInfo == null) {
        /**
         * fetches a [PokemonInfo] from the network and getting [ApiResponse] asynchronously.
         * @see [suspendOnSuccess](https://github.com/skydoves/sandwich#apiresponse-extensions-for-coroutines)
         */
        val response = pokedexClient.fetchPokemonInfo(name = name)
        response.suspendOnSuccess {
          pokemonInfoDao.insertPokemonInfo(data.asEntity())
          emit(data)
        }
          // handles the case when the API request gets an error response.
          // e.g., internal server error.
          .onError {
            /** maps the [ApiResponse.Failure.Error] to the [PokemonErrorResponse] using the mapper. */
            map(ErrorResponseMapper) { onError("[Code: $code]: $message") }
          }
          // handles the case when the API request gets an exception response.
          // e.g., network connection error.
          .onException { onError(message) }
      } else {
        emit(pokemonInfo.asDomain())
      }
    }.onCompletion { onComplete() }.flowOn(ioDispatcher)
}
