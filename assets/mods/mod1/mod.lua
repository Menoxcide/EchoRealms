-- mod.lua - Fun Move Counter Mod
local moveCount = 0

function onPlayerMove(x, y)
    moveCount = moveCount + 1
    showMessage("Player moved to " .. x .. ", " .. y .. ". Total moves: " .. moveCount)

    if moveCount % 10 == 0 then
        showMessage("Level up! You've made " .. moveCount .. " moves in EchoRealms. Keep exploring!")
        -- Hypothetical: spawnReward(x, y) -- If exposed
    elseif moveCount % 5 == 0 then
        showMessage("Halfway to level up! Fun fact: EchoRealms has infinite adventures.")
    end
end
